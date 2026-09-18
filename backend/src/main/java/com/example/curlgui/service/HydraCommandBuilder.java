package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.curlgui.dto.HydraAttackConfigDto;

/**
 * Builds the {@code hydra} argument list for one {@code http-post-form} /
 * {@code https-post-form} attack. Pure: no I/O, no process, no Spring - it
 * only validates the structured config and turns it into a
 * {@code List<String>} that {@link HydraAttackService} hands to
 * {@link ProcessBuilder}. Mirrors the {@link CurlCommandBuilder} split: file
 * existence (the executable, the wordlist) is I/O and stays in
 * {@link HydraAttackService}.
 *
 * <p>Every element of the returned list is a separate argv entry - there is no
 * shell, so the target, credentials and module string cannot be interpreted as
 * shell metacharacters or extra command-line options.
 *
 * <p>{@code invocationPrefix} is how Hydra itself gets invoked: a single
 * element (the executable path) for a local run, or the full
 * {@code wsl.exe -d <distro> --exec <hydraPath>} sequence when Hydra is
 * launched inside WSL (see {@link HydraAttackService}). Either way it is just prepended
 * to the same Hydra arguments - the target/credential/module logic below is
 * identical for both.
 */
final class HydraCommandBuilder {

    private HydraCommandBuilder() {
    }

    /** Convenience for a local run: a single executable as the whole invocation. */
    static List<String> build(String executablePath, HydraAttackConfigDto config) {
        return build(List.of(executablePath), config);
    }

    static List<String> build(List<String> invocationPrefix, HydraAttackConfigDto config) {
        if (invocationPrefix == null || invocationPrefix.isEmpty()) {
            throw new InvalidRequestException("The Hydra invocation command is required.");
        }
        if (config == null) {
            throw new InvalidRequestException("Hydra attack configuration is required.");
        }

        String host = requireNonBlank(config.host(), "Target host is required.");
        Integer port = config.port();
        if (port == null || port < 1 || port > 65535) {
            throw new InvalidRequestException("Port must be between 1 and 65535.");
        }
        String protocol = normalizeProtocol(config.protocol());
        String username = requireNonBlank(config.username(), "Username is required.");
        String wordlistPath = requireNonBlank(config.wordlistPath(), "Password wordlist path is required.");
        String path = requireNonBlank(config.path(), "Path is required.");
        if (!path.startsWith("/")) {
            throw new InvalidRequestException("Path must start with \"/\".");
        }
        String formParams = requireNonBlank(config.formParams(), "Form parameters are required.");
        if (!formParams.contains("^USER^") || !formParams.contains("^PASS^")) {
            throw new InvalidRequestException(
                    "Form parameters must include the ^USER^ and ^PASS^ placeholders.");
        }
        String failureCondition = requireNonBlank(config.failureCondition(), "Failure condition is required.");
        requireNoColon(path, "Path");
        requireNoColon(formParams, "Form parameters");
        requireNoColon(failureCondition, "Failure condition");

        String moduleString = path + ":" + formParams + ":F=" + failureCondition;

        List<String> argv = new ArrayList<>(invocationPrefix);
        argv.add("-l");
        argv.add(username);
        argv.add("-P");
        argv.add(wordlistPath);
        argv.add("-s");
        argv.add(String.valueOf(port));
        argv.add(host);
        argv.add(protocol.equals("https") ? "https-post-form" : "http-post-form");
        argv.add(moduleString);
        argv.add("-V");
        return argv;
    }

    private static String normalizeProtocol(String raw) {
        String p = (raw == null || raw.isBlank()) ? "http" : raw.trim().toLowerCase(Locale.ROOT);
        if (!p.equals("http") && !p.equals("https")) {
            throw new InvalidRequestException("Protocol must be \"http\" or \"https\".");
        }
        return p;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidRequestException(message);
        }
        return value.trim();
    }

    private static void requireNoColon(String value, String fieldName) {
        if (value.contains(":")) {
            throw new InvalidRequestException(
                    fieldName + " must not contain \":\" - it is used as the Hydra module field separator.");
        }
    }
}
