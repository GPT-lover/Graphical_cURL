package com.example.curlgui.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.curlgui.dto.CookieDto;
import com.example.curlgui.dto.CurlOptionsDto;
import com.example.curlgui.dto.HeaderDto;

/**
 * Executes one HTTP request by running the real {@code curl} executable via
 * {@link ProcessBuilder} (argument-list form - never a shell, never a
 * concatenated command string), and captures the response.
 *
 * <p>Replaces the previous {@code java.net.http.HttpClient} path: sites behind
 * WAF/bot fingerprinting (e.g. Vercel's security checkpoint) were rejecting the
 * JDK client's TLS/HTTP-2 fingerprint while accepting the identical request from
 * CLI curl.
 *
 * <h3>Response capture</h3>
 * Body, headers and metadata are captured into <em>separate</em> temp files
 * ({@code --output} / {@code --dump-header} / {@code --write-out}); nothing uses
 * {@code curl -i}. The response body is read back as raw bytes, so binary bodies
 * are preserved exactly up to the single charset decode {@code RequestService}
 * performs for the DTO.
 *
 * <h3>Logging</h3>
 * Only method, host, header <em>count</em>, body <em>size</em>, exit code,
 * status and duration are logged - never a header value, cookie, token, request
 * body, proxy string or the full URL.
 */
@Component
public class CurlProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(CurlProcessExecutor.class);

    /** Matches the previous Java client (HttpClientConfig connectTimeout 10s). */
    static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    /** Matches the previous Java client (RequestService REQUEST_TIMEOUT 30s). */
    static final int DEFAULT_MAX_TIME_SECONDS = 30;
    /** Extra time the Java watchdog waits beyond curl's own {@code --max-time}. */
    private static final int WATCHDOG_SLACK_SECONDS = 5;
    /** Refuse to spawn if the argv would blow the OS command-line limit (~32k on Windows). */
    private static final int MAX_COMMAND_LINE = 30_000;

    /** Optional override for the curl executable (absolute path or a name on PATH). */
    private final String curlPathOverride;

    private volatile boolean probed = false;
    private volatile String curlVersionLine = null;

    public CurlProcessExecutor(@Value("${app.curl.path:}") String curlPathOverride) {
        this.curlPathOverride = curlPathOverride == null ? "" : curlPathOverride.trim();
    }

    /**
     * What {@code curl} produced. {@code httpVersion} is informational (logged /
     * tested) - the response DTO does not carry it, so the frontend is unchanged.
     */
    public record Result(int statusCode,
                         String httpVersion,
                         Map<String, String> headers,
                         byte[] body,
                         long durationMs,
                         List<String> warnings) {
    }

    /**
     * Run the request. Adds any advisory messages (cookie conflicts, TLS-verify
     * disabled) to {@code warnings}.
     *
     * @throws RequestExecutionException for every failure to <em>perform</em> the
     *         request: curl missing, curl failed to start, timeout, or a non-zero
     *         curl exit. A completed HTTP response - including 4xx / 5xx / 429 -
     *         is returned normally in {@link Result}, never thrown.
     */
    public Result execute(String method, URI uri, String body,
                          List<HeaderDto> headers, List<CookieDto> cookies,
                          CurlOptionsDto options, List<String> warnings) {

        ensureCurlAvailable();

        CurlOptionsDto opt = CurlOptionsDto.orNone(options);

        String manualCookieHeader = CookieHeader.extractManualCookieHeader(headers);
        CookieHeader.Result cookieResult = CookieHeader.resolve(cookies, manualCookieHeader);
        warnings.addAll(cookieResult.warnings());
        if (opt.insecure()) {
            warnings.add("TLS certificate verification was disabled for this request (-k).");
        }

        Path dir;
        try {
            dir = Files.createTempDirectory("curlgui-");
        } catch (IOException ex) {
            throw new RequestExecutionException(
                    "Could not create a temporary working directory for the request",
                    ex.getMessage());
        }
        Path bodyOut = dir.resolve("body");
        Path headerDump = dir.resolve("headers");
        Path stdoutFile = dir.resolve("stdout");
        Path stderrFile = dir.resolve("stderr");
        Path dataFile = null;
        deleteOnExit(dir, bodyOut, headerDump, stdoutFile, stderrFile);

        try {
            String dataFileArg = null;
            if (body != null && !body.isEmpty()) {
                dataFile = dir.resolve("data");
                Files.write(dataFile, body.getBytes(StandardCharsets.UTF_8));
                dataFile.toFile().deleteOnExit();
                dataFileArg = dataFile.toString();
            }

            List<String> argv = CurlCommandBuilder.build(
                    binary(), method, uri.toString(), headers, cookieResult.value(),
                    dataFileArg, headerDump.toString(), bodyOut.toString(), opt,
                    DEFAULT_CONNECT_TIMEOUT_SECONDS, DEFAULT_MAX_TIME_SECONDS);

            long approxLen = 0;
            for (String s : argv) {
                approxLen += s.length() + 3L;
            }
            if (approxLen > MAX_COMMAND_LINE) {
                throw new RequestExecutionException(
                        "This request has too many or too large headers to pass to curl on this platform.",
                        "argv is about " + approxLen + " chars; limit is " + MAX_COMMAND_LINE);
            }

            int effectiveMaxTime = positiveOr(opt.maxTimeSeconds(), DEFAULT_MAX_TIME_SECONDS);
            long bodyBytesCount = dataFile == null ? 0 : Files.size(dataFile);
            log.info("curl {} -> host \"{}\" ({} header row(s), body={} bytes, cookies={})",
                    method, uri.getHost(),
                    headers == null ? 0 : headers.size(),
                    bodyBytesCount, cookieResult.value() != null);

            ProcessBuilder pb = new ProcessBuilder(argv);
            pb.redirectOutput(stdoutFile.toFile());
            pb.redirectError(stderrFile.toFile());

            Process process;
            try {
                process = pb.start();
            } catch (IOException ex) {
                throw startFailure(ex);
            }

            boolean finished;
            try {
                finished = process.waitFor(effectiveMaxTime + WATCHDOG_SLACK_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new RequestExecutionException("The request was interrupted before it completed",
                        ex.getMessage());
            }

            if (!finished) {
                process.destroyForcibly();
                try {
                    process.waitFor(3, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                log.warn("curl request to \"{}\" timed out after ~{}s; the process was killed",
                        uri.getHost(), effectiveMaxTime);
                throw new RequestExecutionException(
                        "The request timed out after about " + effectiveMaxTime + "s",
                        "curl did not finish; its process was terminated");
            }

            int exit = process.exitValue();
            String stderrTail = safeTail(readString(stderrFile));

            if (exit != 0) {
                log.warn("curl request to \"{}\" failed: exit={} :: {}",
                        uri.getHost(), exit, oneLine(stderrTail));
                throw mapCurlExit(exit, stderrTail, uri);
            }

            // exit 0 -> a complete HTTP exchange, even if the status is 4xx/5xx.
            String[] writeOut = parseWriteOut(readString(stdoutFile));
            int statusCode = resolveStatus(writeOut, headerDump);
            String httpVersion = (writeOut != null && writeOut.length > 1) ? writeOut[1] : "";
            long durationMs = (writeOut != null && writeOut.length > 3)
                    ? Math.round(parseDoubleSafe(writeOut[3]) * 1000.0)
                    : 0L;
            Map<String, String> responseHeaders = parseHeaderDump(readBytes(headerDump));
            byte[] bodyBytes = Files.exists(bodyOut) ? Files.readAllBytes(bodyOut) : new byte[0];

            log.info("curl exit=0 status={} httpVersion={} in {}ms ({} response bytes)",
                    statusCode, httpVersion.isEmpty() ? "?" : httpVersion, durationMs, bodyBytes.length);

            return new Result(statusCode, displayVersion(httpVersion), responseHeaders,
                    bodyBytes, durationMs, warnings);

        } catch (IOException ex) {
            throw new RequestExecutionException("Could not run curl for this request", ex.getMessage());
        } finally {
            deleteQuietly(bodyOut);
            deleteQuietly(headerDump);
            deleteQuietly(stdoutFile);
            deleteQuietly(stderrFile);
            if (dataFile != null) {
                deleteQuietly(dataFile);
            }
            deleteQuietly(dir);
        }
    }

    // ------------------------------------------------------------------
    // curl discovery
    // ------------------------------------------------------------------

    /** Windows -> curl.exe, otherwise -> curl, unless {@code app.curl.path} overrides it. */
    private String binary() {
        if (!curlPathOverride.isEmpty()) {
            return curlPathOverride;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "curl.exe" : "curl";
    }

    /** One cheap {@code curl --version} probe, cached on success (retried if it fails). */
    private void ensureCurlAvailable() {
        if (probed) {
            return;
        }
        synchronized (this) {
            if (probed) {
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(binary(), "--version");
            pb.redirectErrorStream(true);
            Process p;
            try {
                p = pb.start();
            } catch (IOException ex) {
                throw startFailure(ex);
            }
            byte[] out;
            boolean done;
            try {
                out = p.getInputStream().readNBytes(4096);
                done = p.waitFor(5, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException ex) {
                p.destroyForcibly();
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new RequestExecutionException("Could not verify the curl executable", ex.getMessage());
            }
            if (!done) {
                p.destroyForcibly();
                throw new RequestExecutionException("curl did not respond to --version", null);
            }
            if (p.exitValue() != 0) {
                throw new RequestExecutionException(
                        "curl is present but \"curl --version\" failed (exit " + p.exitValue() + ")", null);
            }
            curlVersionLine = firstLine(new String(out, StandardCharsets.UTF_8));
            log.info("curl executor ready: {}", curlVersionLine);
            probed = true;
        }
    }

    private RequestExecutionException startFailure(IOException ex) {
        String m = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        boolean missing = m.contains("cannot run program")
                || m.contains("createprocess error=2")
                || m.contains("error=2,")
                || m.contains("no such file or directory");
        if (missing) {
            return new RequestExecutionException(
                    "The curl executable was not found",
                    "Install curl and ensure it is on PATH, or set app.curl.path to its full path. "
                            + "Windows 10/11 ship curl.exe with the OS.");
        }
        return new RequestExecutionException("Could not start the curl process", ex.getMessage());
    }

    // ------------------------------------------------------------------
    // exit-code mapping
    // ------------------------------------------------------------------

    private RequestExecutionException mapCurlExit(int exit, String stderrTail, URI uri) {
        String message = switch (exit) {
            case 28 -> "The request timed out";
            case 6 -> "Could not resolve host \"" + uri.getHost() + "\"";
            case 7 -> "Could not connect to the target server";
            case 5 -> "Could not resolve the proxy given in the request";
            case 35, 51, 53, 54, 58, 59, 60, 66, 77, 80, 82, 83, 90, 91 ->
                    "TLS/SSL error while contacting the target server";
            case 47, 56 -> "Network error while receiving the response";
            case 52 -> "The target server closed the connection without a response";
            case 3 -> "curl rejected the request URL as malformed";
            case 2 -> "curl rejected the request options";
            default -> "curl failed (exit code " + exit + ")";
        };
        String detail = (stderrTail == null || stderrTail.isBlank())
                ? "curl exit code " + exit
                : stderrTail;
        return new RequestExecutionException(message, detail);
    }

    // ------------------------------------------------------------------
    // parsing helpers
    // ------------------------------------------------------------------

    /** Last non-blank line of the {@code --write-out} output, split on whitespace, or null. */
    private static String[] parseWriteOut(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return null;
        }
        String last = null;
        for (String line : stdout.split("\r?\n")) {
            if (!line.isBlank()) {
                last = line.trim();
            }
        }
        return last == null ? null : last.split("\\s+");
    }

    private int resolveStatus(String[] writeOut, Path headerDump) {
        if (writeOut != null && writeOut.length > 0) {
            try {
                int code = Integer.parseInt(writeOut[0]);
                if (code >= 100) {
                    return code;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the header block
            }
        }
        Integer fromHeaders = statusFromHeaderBlock(readString(headerDump));
        if (fromHeaders != null) {
            return fromHeaders;
        }
        throw new RequestExecutionException(
                "curl completed but did not report an HTTP status code",
                "no %{http_code} on stdout and no status line in the header dump");
    }

    private static Integer statusFromHeaderBlock(String dump) {
        String block = lastHttpBlock(dump);
        if (block == null) {
            return null;
        }
        String statusLine = block.split("\n", 2)[0].trim();
        String[] parts = statusLine.split("\\s+");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Parse the LAST response header block from a {@code --dump-header} file
     * (with {@code -L} there is one block per hop). Folds obs-fold continuation
     * lines; joins duplicate header names with {@code ", "} - matching how the
     * previous Java path flattened multi-valued headers.
     */
    private static Map<String, String> parseHeaderDump(byte[] raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (raw == null || raw.length == 0) {
            return headers;
        }
        String block = lastHttpBlock(new String(raw, StandardCharsets.ISO_8859_1));
        if (block == null) {
            return headers;
        }
        String[] lines = block.split("\n");
        String lastName = null;
        for (int i = 1; i < lines.length; i++) {          // line 0 = status line
            String line = stripTrailingCr(lines[i]);
            if (line.isEmpty()) {
                continue;
            }
            if ((line.charAt(0) == ' ' || line.charAt(0) == '\t') && lastName != null) {
                headers.merge(lastName, " " + line.trim(), (a, b) -> a + b);
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).strip();
            if (headers.containsKey(name)) {
                headers.put(name, headers.get(name) + ", " + value);
            } else {
                headers.put(name, value);
            }
            lastName = name;
        }
        return headers;
    }

    /** The last block that starts with "HTTP/" in a header dump, or null. */
    private static String lastHttpBlock(String dump) {
        if (dump == null || dump.isBlank()) {
            return null;
        }
        String normalised = dump.replace("\r\n", "\n");
        String[] blocks = normalised.split("\n\n");
        String found = null;
        for (String b : blocks) {
            String t = b.strip();
            if (t.startsWith("HTTP/")) {
                found = t;
            }
        }
        return found;
    }

    // ------------------------------------------------------------------
    // small utilities
    // ------------------------------------------------------------------

    private static int positiveOr(Integer value, int fallback) {
        return (value != null && value > 0) ? value : fallback;
    }

    private static double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (RuntimeException ex) {
            return 0.0;
        }
    }

    private static String displayVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return switch (raw.trim()) {
            case "1.0" -> "HTTP/1.0";
            case "1.1" -> "HTTP/1.1";
            case "2", "2.0" -> "HTTP/2";
            case "3", "3.0" -> "HTTP/3";
            default -> raw.trim();
        };
    }

    private static String readString(Path p) {
        return new String(readBytes(p), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(Path p) {
        try {
            return Files.exists(p) ? Files.readAllBytes(p) : new byte[0];
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return (nl < 0 ? s : s.substring(0, nl)).trim();
    }

    private static String stripTrailingCr(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    /** Short, single-paragraph tail of curl's stderr for an error detail (no secrets: we never pass -v). */
    private static String safeTail(String stderr) {
        if (stderr == null) {
            return "";
        }
        String trimmed = stderr.strip();
        if (trimmed.length() > 300) {
            trimmed = trimmed.substring(trimmed.length() - 300);
        }
        String[] lines = trimmed.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lines.length - 2); i < lines.length; i++) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(lines[i].trim());
        }
        return sb.toString();
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static void deleteOnExit(Path... paths) {
        for (Path p : paths) {
            p.toFile().deleteOnExit();
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // backstopped by deleteOnExit()
        }
    }
}
