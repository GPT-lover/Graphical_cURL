package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.example.curlgui.dto.CurlOptionsDto;
import com.example.curlgui.dto.HeaderDto;

/**
 * Builds the {@code curl} argument list for one request. Pure: no I/O, no
 * process, no Spring - it only turns structured request data into a
 * {@code List<String>} that {@link CurlProcessExecutor} hands to
 * {@link ProcessBuilder}. Directly unit-testable, and mirrors the
 * {@link CurlGeneratorService} / {@link ShellQuote} split already used for the
 * "Copy as cURL" feature.
 *
 * <p>Every element of the returned list is passed to {@code curl} as a separate
 * argv entry - there is no shell, so header/cookie/URL/body values need no
 * quoting and cannot be interpreted as options or shell metacharacters.
 *
 * <h3>Response capture (never {@code curl -i})</h3>
 * <ul>
 *   <li>{@code --output <bodyFile>}      - response body, raw bytes, binary-safe</li>
 *   <li>{@code --dump-header <hdrFile>}  - response header block(s)</li>
 *   <li>{@code --write-out <format>}     - status / version / sizes to stdout</li>
 * </ul>
 * so body, headers and metadata never contaminate each other.
 */
final class CurlCommandBuilder {

    private CurlCommandBuilder() {
    }

    /** The {@code --write-out} format: one line, space-separated, trailing newline. */
    static final String WRITE_OUT_FORMAT =
            "\n%{http_code} %{http_version} %{size_download} %{time_total} %{num_redirects}\n";

    /**
     * @param curlBinary       "curl.exe" / "curl" / an absolute path
     * @param method           normalised, upper-case HTTP method
     * @param url              validated absolute http/https URL
     * @param headers          editor headers, in order (a row named "Cookie" is skipped)
     * @param cookieHeaderValue the single combined {@code Cookie} value, or null
     * @param dataFilePath     path to a temp file holding the request body, or null for no body
     * @param headerDumpPath   where curl should write response headers
     * @param bodyOutputPath   where curl should write the response body
     * @param options          transport options (never null - use {@link CurlOptionsDto#orNone})
     * @param defaultConnectTimeoutSeconds used when the command did not specify one
     * @param defaultMaxTimeSeconds        used when the command did not specify one
     */
    static List<String> build(String curlBinary,
                              String method,
                              String url,
                              List<HeaderDto> headers,
                              String cookieHeaderValue,
                              String dataFilePath,
                              String headerDumpPath,
                              String bodyOutputPath,
                              CurlOptionsDto options,
                              int defaultConnectTimeoutSeconds,
                              int defaultMaxTimeSeconds) {

        CurlOptionsDto opt = CurlOptionsDto.orNone(options);
        boolean isHead = "HEAD".equals(method);
        List<String> a = new ArrayList<>();

        a.add(curlBinary);

        // Must be first: ignore ~/.curlrc so a user's global curl config cannot
        // silently add options to requests this app runs.
        a.add("--disable");

        // Quiet, but still emit real errors to stderr.
        a.add("--silent");
        a.add("--show-error");

        // Separate capture of body / headers / metadata.
        a.add("--output");
        a.add(bodyOutputPath);
        a.add("--dump-header");
        a.add(headerDumpPath);
        a.add("--write-out");
        a.add(WRITE_OUT_FORMAT);

        // Timeouts: the command's own values win; otherwise the app defaults
        // (which match the previous Java client's 10s connect / 30s total).
        a.add("--connect-timeout");
        a.add(String.valueOf(positiveOr(opt.connectTimeoutSeconds(), defaultConnectTimeoutSeconds)));
        a.add("--max-time");
        a.add(String.valueOf(positiveOr(opt.maxTimeSeconds(), defaultMaxTimeSeconds)));

        if (opt.compressed()) {
            a.add("--compressed");
        }
        String httpVersionFlag = httpVersionFlag(opt.httpVersion());
        if (httpVersionFlag != null) {
            a.add(httpVersionFlag);
        }
        if (opt.followRedirects()) {
            a.add("--location");
        }
        if (opt.insecure()) {
            a.add("--insecure");
        }
        if (opt.hasProxy()) {
            a.add("--proxy");
            a.add(opt.proxy());
        }
        if (opt.hasProxyUser()) {
            a.add("--proxy-user");
            a.add(opt.proxyUser());
        }

        // Method. HEAD -> --head (never "--request HEAD", which makes curl wait
        // for a body that servers do not send). GET-with-a-body stays GET.
        if (isHead) {
            a.add("--head");
        } else {
            a.add("--request");
            a.add(method);
        }

        // Headers, in editor order. A literal "Cookie" row is emitted once below
        // from the combined value instead.
        if (headers != null) {
            for (HeaderDto header : headers) {
                if (header == null) {
                    continue;
                }
                String name = header.key() == null ? "" : header.key().trim();
                if (name.isEmpty() || name.equalsIgnoreCase("Cookie")) {
                    continue;
                }
                String value = header.value() == null ? "" : header.value();
                a.add("--header");
                a.add(name + ": " + value);
            }
        }
        if (cookieHeaderValue != null && !cookieHeaderValue.isBlank()) {
            a.add("--header");
            a.add("Cookie: " + cookieHeaderValue);
        }

        // Request body: always from a file, so it is binary-safe and never hits
        // the OS command-line length limit. --data-binary sends it verbatim
        // (the closest match to Chrome's --data-raw). Skipped for HEAD.
        if (!isHead && dataFilePath != null) {
            a.add("--data-binary");
            a.add("@" + dataFilePath);
        }

        // The URL last, and via --url so a value starting with '-' can never be
        // read as an option (it is also validated as http/https before we get here).
        a.add("--url");
        a.add(url);

        return a;
    }

    private static int positiveOr(Integer value, int fallback) {
        return (value != null && value > 0) ? value : fallback;
    }

    /** Map a CurlOptionsDto.httpVersion string to the matching curl flag, or null. */
    static String httpVersionFlag(String httpVersion) {
        if (httpVersion == null) {
            return null;
        }
        return switch (httpVersion.trim().toLowerCase(Locale.ROOT)) {
            case "1.0" -> "--http1.0";
            case "1.1" -> "--http1.1";
            case "2" -> "--http2";
            case "2-prior-knowledge" -> "--http2-prior-knowledge";
            default -> null;
        };
    }
}
