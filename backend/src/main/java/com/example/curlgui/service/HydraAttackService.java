package com.example.curlgui.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.curlgui.dto.HydraAttackConfigDto;
import com.example.curlgui.dto.HydraAttackStartedDto;
import com.example.curlgui.dto.HydraAttackStatusDto;
import com.example.curlgui.dto.HydraDetectResultDto;
import com.example.curlgui.dto.HydraSettingsDto;

import jakarta.annotation.PreDestroy;

/**
 * Launches THC Hydra as an external process - never bundled, never
 * reimplemented - and lets the frontend poll its output and stop it, the same
 * "start -&gt; poll -&gt; stop" shape as {@link RunMultipleService}.
 *
 * <p>Two execution modes ({@link HydraExecutionMode}):
 * <ul>
 *   <li>{@code LOCAL} - the configured executable is run directly.</li>
 *   <li>{@code WSL} - the backend runs on Windows but Hydra lives inside a WSL
 *       distribution (e.g. Kali Linux). Every invocation - the attack itself,
 *       the {@code -h} detect probe, and the wordlist path check - is wrapped
 *       as {@code wsl.exe -d <distro> --exec <command...>}, still as a plain
 *       {@link ProcessBuilder} argument list (no shell string is ever built).
 *       {@code --exec} (rather than {@code --}) is required: WSL's {@code --}
 *       still runs the trailing command line through the distro's default
 *       shell, so a value containing {@code &}, {@code |}, {@code <}, {@code >}
 *       etc. (e.g. a Hydra {@code http-post-form} module string, which is
 *       exactly that) gets re-interpreted there even though this side never
 *       builds a shell string. {@code --exec} executes it directly (execve),
 *       preserving argv boundaries and literal characters as passed.
 *       A Windows-style wordlist path (e.g. {@code C:\...}) is translated to
 *       its WSL equivalent (e.g. {@code /mnt/c/...}) with WSL's own
 *       {@code wslpath} utility rather than manual path parsing; a path that
 *       already looks like a WSL/Linux path is passed through unchanged.</li>
 * </ul>
 *
 * <p>Only one attack may run at a time: Hydra attacks are noisy, long-running
 * and target a real system, so silently allowing a second one to start
 * alongside an untracked first one would be the wrong default for this
 * single-user desktop app.
 */
@Service
public class HydraAttackService {

    private static final Logger log = LoggerFactory.getLogger(HydraAttackService.class);
    private static final int DETECT_TIMEOUT_SECONDS = 20; // generous: a cold WSL VM start can take several seconds
    private static final int WSL_HELPER_TIMEOUT_SECONDS = 20; // wslpath / test -r probes - same cold-start cost
    private static final long RETENTION_MS = 10 * 60 * 1000L;

    /** A Windows absolute path: a drive letter, colon, then a slash or backslash (e.g. "C:\Users\..." or "C:/Users/..."). */
    private static final Pattern WINDOWS_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*");

    private final Map<String, HydraAttackState> attacks = new ConcurrentHashMap<>();
    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 3, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), daemon("hydra-attack"));

    @PreDestroy
    void shutdown() {
        attacks.values().forEach(this::killQuietly);
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------

    /** Runs Hydra's {@code -h} (via WSL if configured) and reports whether it looks like Hydra. Never throws. */
    public HydraDetectResultDto detect(HydraSettingsDto settings) {
        HydraExecutionMode mode = HydraExecutionMode.parse(settings == null ? null : settings.executionMode());
        try {
            List<String> argv = (mode == HydraExecutionMode.WSL)
                    ? wslInvocation(requireWslDistro(settings.wslDistro()), requireWslHydraPath(settings.wslHydraPath()), "-h")
                    : List.of(requireLocalExecutable(settings.executablePath()), "-h");
            if (mode == HydraExecutionMode.LOCAL) {
                requireExecutableFile(argv.get(0));
            }
            return runDetectProbe(argv);
        } catch (InvalidRequestException ex) {
            return new HydraDetectResultDto(false, ex.getMessage());
        }
    }

    private HydraDetectResultDto runDetectProbe(List<String> argv) {
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException ex) {
            return new HydraDetectResultDto(false, "Could not start \"" + argv.get(0) + "\": " + ex.getMessage());
        }

        String output;
        boolean finished;
        try {
            output = decodeWslOutput(p.getInputStream().readNBytes(8192));
            finished = p.waitFor(DETECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException ex) {
            p.destroyForcibly();
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new HydraDetectResultDto(false, "Could not read Hydra's output: " + ex.getMessage());
        }
        if (!finished) {
            p.destroyForcibly();
            return new HydraDetectResultDto(false,
                    "Hydra did not respond within " + DETECT_TIMEOUT_SECONDS + "s.");
        }
        if (!output.toLowerCase(Locale.ROOT).contains("hydra")) {
            String detail = firstNonBlankLine(output);
            return new HydraDetectResultDto(false,
                    "The configured command does not look like Hydra."
                            + (detail != null ? " (" + detail + ")" : ""));
        }
        String firstLine = firstNonBlankLine(output);
        return new HydraDetectResultDto(true, firstLine != null ? firstLine : "Hydra is available.");
    }

    public HydraAttackStartedDto start(HydraSettingsDto settings, HydraAttackConfigDto config) {
        sweepOldAttacks();

        boolean alreadyRunning = attacks.values().stream()
                .anyMatch(s -> s.status == HydraAttackState.Status.RUNNING);
        if (alreadyRunning) {
            throw new ConflictException(
                    "A Hydra attack is already running. Stop it before starting a new one.");
        }

        HydraExecutionMode mode = HydraExecutionMode.parse(settings == null ? null : settings.executionMode());
        List<String> invocationPrefix;
        String cleanupDistro = null;
        String cleanupHydraPath = null;

        if (mode == HydraExecutionMode.WSL) {
            String distro = requireWslDistro(settings.wslDistro());
            String hydraPath = requireWslHydraPath(settings.wslHydraPath());
            invocationPrefix = wslInvocation(distro, hydraPath);
            cleanupDistro = distro;
            cleanupHydraPath = hydraPath;
        } else {
            String exe = requireLocalExecutable(settings == null ? null : settings.executablePath());
            requireExecutableFile(exe);
            invocationPrefix = List.of(exe);
        }

        HydraAttackConfigDto resolvedConfig = resolveWordlistPath(mode, cleanupDistro, config);
        List<String> argv = HydraCommandBuilder.build(invocationPrefix, resolvedConfig);

        String id = UUID.randomUUID().toString();
        HydraAttackState state = new HydraAttackState(id);
        state.wslCleanupDistro = cleanupDistro;
        state.wslCleanupHydraPath = cleanupHydraPath;
        attacks.put(id, state);

        log.info("Hydra attack {} starting: mode={}, host={}, port={}, protocol={}",
                id, mode, config.host(), config.port(), config.protocol());
        executor.submit(() -> runAttack(state, argv));
        return new HydraAttackStartedDto(id);
    }

    public HydraAttackStatusDto status(String attackId, int offset) {
        HydraAttackState state = require(attackId);
        state.lastTouchedAtMillis = System.currentTimeMillis();
        return new HydraAttackStatusDto(
                state.status.name(), state.outputFrom(offset), state.exitCode, state.errorMessage);
    }

    public void stop(String attackId) {
        HydraAttackState state = require(attackId);
        state.cancelled.set(true);
        Process process = state.process;
        if (process != null && process.isAlive()) {
            process.destroy();
            executor.submit(() -> forceKillIfStillAlive(process));
        }
        // Destroying the wsl.exe client above does not reliably stop the actual
        // Hydra process running inside the WSL VM, so reach in and kill it too.
        if (state.wslCleanupDistro != null) {
            executor.submit(() -> killWslHydraQuietly(state.wslCleanupDistro, state.wslCleanupHydraPath));
        }
    }

    // ------------------------------------------------------------------
    // WSL helpers
    // ------------------------------------------------------------------

    private static List<String> wslInvocation(String distro, String hydraPath, String... extraArgs) {
        List<String> argv = new java.util.ArrayList<>(List.of("wsl.exe", "-d", distro, "--exec", hydraPath));
        for (String extra : extraArgs) {
            argv.add(extra);
        }
        return argv;
    }

    /**
     * If {@code config}'s wordlist path looks like a Windows path, validates it
     * exists on the Windows side and (for WSL mode) translates it via
     * {@code wslpath}; a path that already looks like a WSL/Linux path is
     * checked for readability inside WSL instead. Returns {@code config}
     * unchanged when nothing needs converting (including when the path is
     * blank - {@link HydraCommandBuilder} reports that uniformly).
     */
    private HydraAttackConfigDto resolveWordlistPath(HydraExecutionMode mode, String wslDistro,
                                                      HydraAttackConfigDto config) {
        if (config == null) {
            return null;
        }
        String raw = config.wordlistPath();
        if (raw == null || raw.isBlank()) {
            return config;
        }
        String trimmed = raw.trim();

        if (mode == HydraExecutionMode.LOCAL) {
            requireReadableFile(trimmed, "password wordlist");
            return config;
        }

        if (looksLikeWindowsPath(trimmed)) {
            requireReadableFile(trimmed, "password wordlist");
            String converted = convertWindowsPathForWsl(wslDistro, trimmed);
            return withWordlistPath(config, converted);
        }

        requireReadableFileInWsl(wslDistro, trimmed, "password wordlist");
        return config;
    }

    static boolean looksLikeWindowsPath(String path) {
        return path != null && WINDOWS_PATH.matcher(path).matches();
    }

    private static HydraAttackConfigDto withWordlistPath(HydraAttackConfigDto config, String wordlistPath) {
        return new HydraAttackConfigDto(config.host(), config.port(), config.protocol(), config.username(),
                wordlistPath, config.path(), config.formParams(), config.failureCondition());
    }

    /** Runs {@code wslpath -a <windowsPath>} inside {@code distro} and returns the converted absolute WSL path. */
    private String convertWindowsPathForWsl(String distro, String windowsPath) {
        List<String> argv = List.of("wsl.exe", "-d", distro, "--exec", "wslpath", "-a", windowsPath);
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException ex) {
            throw new InvalidRequestException(
                    "Could not run wslpath (inside WSL distro \"" + distro + "\") to convert the wordlist path: "
                            + ex.getMessage());
        }
        String output;
        boolean finished;
        try {
            output = decodeWslOutput(p.getInputStream().readAllBytes());
            finished = p.waitFor(WSL_HELPER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException ex) {
            p.destroyForcibly();
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new InvalidRequestException("Could not read wslpath's output: " + ex.getMessage());
        }
        if (!finished) {
            p.destroyForcibly();
            throw new InvalidRequestException("Converting the wordlist path via wslpath timed out.");
        }
        String converted = output.strip();
        if (p.exitValue() != 0 || converted.isEmpty()) {
            throw new InvalidRequestException("Could not convert the wordlist path \"" + windowsPath
                    + "\" for WSL distro \"" + distro + "\" (wslpath exit " + p.exitValue() + "): "
                    + firstNonBlankLine(output));
        }
        log.info("Converted wordlist path for WSL distro \"{}\": \"{}\" -> \"{}\"", distro, windowsPath, converted);
        return converted;
    }

    /** Runs {@code test -r <path>} inside {@code distro} - the Windows side cannot check a WSL/Linux path itself. */
    private void requireReadableFileInWsl(String distro, String path, String label) {
        List<String> argv = List.of("wsl.exe", "-d", distro, "--exec", "test", "-r", path);
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException ex) {
            throw new InvalidRequestException(
                    "Could not check the " + label + " inside WSL distro \"" + distro + "\": " + ex.getMessage());
        }
        boolean finished;
        try {
            p.getInputStream().readAllBytes(); // drain so the process can exit; content unused
            finished = p.waitFor(WSL_HELPER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException ex) {
            p.destroyForcibly();
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new InvalidRequestException("Could not check the " + label + " inside WSL: " + ex.getMessage());
        }
        if (!finished) {
            p.destroyForcibly();
            throw new InvalidRequestException("Checking the " + label + " inside WSL timed out.");
        }
        if (p.exitValue() != 0) {
            throw new InvalidRequestException("The " + label + " was not found or is not readable inside WSL distro \""
                    + distro + "\": " + path);
        }
    }

    /** Best-effort: kill any Hydra process still running inside {@code distro} after the wsl.exe client was destroyed. */
    private void killWslHydraQuietly(String distro, String hydraPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("wsl.exe", "-d", distro, "--exec", "pkill", "-f", hydraPath);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            p.waitFor(WSL_HELPER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (IOException ex) {
            log.warn("Could not send cleanup pkill into WSL distro \"{}\": {}", distro, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------

    private void runAttack(HydraAttackState state, List<String> argv) {
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (IOException ex) {
            state.status = HydraAttackState.Status.ERROR;
            state.errorMessage = "Could not start the Hydra process: " + ex.getMessage();
            state.lastTouchedAtMillis = System.currentTimeMillis();
            log.warn("Hydra attack {} failed to start: {}", state.id, ex.getMessage());
            return;
        }
        state.process = process;

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                state.appendLine(line);
                state.lastTouchedAtMillis = System.currentTimeMillis();
            }
        } catch (IOException ex) {
            state.appendLine("[error reading Hydra output: " + ex.getMessage() + "]");
        }

        int exit;
        try {
            exit = process.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            exit = -1;
        }
        state.exitCode = exit;
        state.status = state.cancelled.get() ? HydraAttackState.Status.STOPPED : HydraAttackState.Status.DONE;
        state.lastTouchedAtMillis = System.currentTimeMillis();
        log.info("Hydra attack {} finished: exit={}, status={}", state.id, exit, state.status);
    }

    private void forceKillIfStillAlive(Process process) {
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void killQuietly(HydraAttackState state) {
        state.cancelled.set(true);
        Process p = state.process;
        if (p != null && p.isAlive()) {
            p.destroy();
            forceKillIfStillAlive(p);
        }
        if (state.wslCleanupDistro != null) {
            killWslHydraQuietly(state.wslCleanupDistro, state.wslCleanupHydraPath);
        }
    }

    private HydraAttackState require(String attackId) {
        HydraAttackState state = attacks.get(attackId);
        if (state == null) {
            throw new NotFoundException("Hydra attack \"" + attackId + "\" not found.");
        }
        return state;
    }

    private void sweepOldAttacks() {
        long now = System.currentTimeMillis();
        attacks.values().removeIf(s -> s.status != HydraAttackState.Status.RUNNING
                && now - s.lastTouchedAtMillis > RETENTION_MS);
    }

    // ---- validation (I/O - kept out of the pure HydraCommandBuilder) ----

    private static String requireLocalExecutable(String executablePath) {
        if (executablePath == null || executablePath.isBlank()) {
            throw new InvalidRequestException(
                    "Hydra executable not configured. Install Hydra separately and select its executable in Settings.");
        }
        return executablePath.trim();
    }

    private static void requireExecutableFile(String exe) {
        Path p = toPath(exe, "Hydra executable");
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new InvalidRequestException(
                    "Hydra executable not found at \"" + exe
                            + "\". Install Hydra separately and select its executable in Settings.");
        }
    }

    private static String requireWslDistro(String distro) {
        if (distro == null || distro.isBlank()) {
            throw new InvalidRequestException(
                    "WSL distribution not configured. Set it in Settings (e.g. \"kali-linux\").");
        }
        return distro.trim();
    }

    private static String requireWslHydraPath(String hydraPath) {
        if (hydraPath == null || hydraPath.isBlank()) {
            throw new InvalidRequestException(
                    "Hydra path inside WSL not configured. Set it in Settings (e.g. \"/usr/bin/hydra\").");
        }
        return hydraPath.trim();
    }

    private static void requireReadableFile(String path, String label) {
        Path p = toPath(path, label);
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new InvalidRequestException("The " + label + " file was not found: " + path);
        }
        if (!Files.isReadable(p)) {
            throw new InvalidRequestException("The " + label + " file is not readable: " + path);
        }
    }

    private static Path toPath(String raw, String label) {
        try {
            return Path.of(raw);
        } catch (InvalidPathException ex) {
            throw new InvalidRequestException("The " + label + " path is not valid: " + raw);
        }
    }

    private static String firstNonBlankLine(String s) {
        if (s == null) {
            return null;
        }
        for (String line : s.split("\r?\n")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return null;
    }

    /**
     * {@code wsl.exe}'s own launcher/service errors (e.g. "There is no
     * distribution with the supplied name.") are printed as UTF-16LE, while
     * anything the actual Linux program inside WSL writes (Hydra's own output,
     * a converted {@code wslpath} result) is whatever encoding it uses -
     * UTF-8 in practice. Detect the former by its distinctive
     * every-other-byte-is-zero pattern (ASCII text encoded as UTF-16LE) so
     * error messages surfaced to the user are readable either way.
     */
    private static String decodeWslOutput(byte[] bytes) {
        return looksLikeUtf16Le(bytes)
                ? new String(bytes, StandardCharsets.UTF_16LE)
                : new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean looksLikeUtf16Le(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return true; // UTF-16LE byte-order mark
        }
        int sample = Math.min(bytes.length, 64) & ~1; // even count, capped
        if (sample < 8) {
            return false;
        }
        int oddZeroCount = 0;
        int oddTotal = 0;
        for (int i = 1; i < sample; i += 2) {
            oddTotal++;
            if (bytes[i] == 0) {
                oddZeroCount++;
            }
        }
        return oddZeroCount * 5 >= oddTotal * 4; // >=80% of odd-offset bytes are zero
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread t = new Thread(runnable, name);
            t.setDaemon(true);
            return t;
        };
    }
}
