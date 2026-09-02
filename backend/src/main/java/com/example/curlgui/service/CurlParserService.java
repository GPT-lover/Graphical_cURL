package com.example.curlgui.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.curlgui.dto.CookieDto;
import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.ParsedRequestDto;

/**
 * Turns a pasted cURL command into a {@link ParsedRequestDto}.
 *
 * <p>Pipeline: {@link CurlTokenizer#tokenize} splits the string into argument
 * tokens, then this class walks the tokens and interprets the options it knows.
 *
 * <p>It is deliberately dependency-free (no {@code HttpClient}, no repository) so
 * it can be unit-tested with a plain {@code new CurlParserService()}.
 *
 * <p>Security: the command is data, never executed. Nothing here runs a shell.
 * We log only <em>counts</em> - never the command, a URL, a header value or a
 * cookie value (any of which can carry credentials).
 */
@Service
public class CurlParserService {

    private static final Logger log = LoggerFactory.getLogger(CurlParserService.class);

    private static final Set<String> HTTP_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    /** Options Java HttpClient handles anyway / that don't belong in the editor. Dropped. */
    private static final Set<String> IGNORED_FLAGS = Set.of(
            "--compressed", "-s", "--silent", "-S", "--show-error", "-v", "--verbose",
            "-i", "--include", "-#", "--progress-bar", "-f", "--fail", "--fail-with-body",
            "--http1.0", "--http1.1", "--http2", "--http2-prior-knowledge", "-0",
            "-k", "--insecure", "-L", "--location", "--location-trusted",
            "-g", "--globoff", "-N", "--no-buffer", "-j", "--junk-session-cookies",
            "--no-keepalive", "-4", "--ipv4", "-6", "--ipv6"
    );

    /** Ignored options that also consume the following token as their value. */
    private static final Set<String> IGNORED_WITH_VALUE = Set.of(
            "--connect-timeout", "-m", "--max-time", "--retry", "--retry-delay",
            "--retry-max-time", "--max-redirs", "--resolve", "--interface", "--limit-rate"
    );

    /** Options that materially change the HTTP request and that we can't yet represent. Fail. */
    private static final Set<String> UNSUPPORTED = Set.of(
            "-F", "--form", "--form-string", "-T", "--upload-file",
            "--data-urlencode", "-G", "--get", "-x", "--proxy", "--proxy-user",
            "-E", "--cert", "--key", "--cacert", "--pinnedpubkey"
    );

    private static final Set<String> DATA_OPTIONS = Set.of(
            "-d", "--data", "--data-raw", "--data-ascii", "--data-binary"
    );

    public ParsedRequestDto parse(String curl) {
        if (curl == null || curl.isBlank()) {
            throw new CurlParseException("Paste a cURL command to import.");
        }

        List<String> tokens = CurlTokenizer.tokenize(curl);
        if (tokens.isEmpty()) {
            throw new CurlParseException("The cURL command was empty.");
        }

        String explicitMethod = null;
        String url = null;
        List<HeaderDto> headers = new ArrayList<>();
        List<CookieDto> cookies = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        boolean hasData = false;
        List<String> warnings = new ArrayList<>();

        int i = 0;
        // Drop a leading "curl" / "curl.exe".
        if (i < tokens.size()) {
            String first = tokens.get(i).toLowerCase(Locale.ROOT);
            if (first.equals("curl") || first.equals("curl.exe")) {
                i++;
            }
        }

        for (; i < tokens.size(); i++) {
            String token = tokens.get(i);

            // Glued short method form, e.g. -XPOST (Chrome doesn't emit this, humans do).
            if (token.length() > 2 && token.charAt(0) == '-' && token.charAt(1) == 'X') {
                explicitMethod = token.substring(2).toUpperCase(Locale.ROOT);
                continue;
            }

            // Support the --option=value form for long options.
            String option = token;
            String inlineValue = null;
            if (token.startsWith("--") && token.indexOf('=') > 2) {
                int eq = token.indexOf('=');
                option = token.substring(0, eq);
                inlineValue = token.substring(eq + 1);
            }

            if (UNSUPPORTED.contains(option)) {
                throw new CurlParseException(
                        "This cURL command uses an option the importer does not support yet: " + option);
            }

            switch (option) {
                case "-X", "--request" -> {
                    explicitMethod = value(option, inlineValue, tokens, i).toUpperCase(Locale.ROOT);
                    if (inlineValue == null) {
                        i++;
                    }
                }
                case "-H", "--header" -> {
                    String raw = value(option, inlineValue, tokens, i);
                    if (inlineValue == null) {
                        i++;
                    }
                    applyHeader(raw, headers, cookies, warnings);
                }
                case "-b", "--cookie" -> {
                    String raw = value(option, inlineValue, tokens, i);
                    if (inlineValue == null) {
                        i++;
                    }
                    if (raw.contains("=")) {
                        parseCookieString(raw, cookies);
                    } else {
                        warnings.add("Ignored a cookie option that pointed at a file rather than name=value pairs.");
                    }
                }
                case "-A", "--user-agent" -> {
                    String v = value(option, inlineValue, tokens, i);
                    if (inlineValue == null) {
                        i++;
                    }
                    upsertHeader(headers, "User-Agent", v);
                }
                case "-e", "--referer" -> {
                    String v = value(option, inlineValue, tokens, i);
                    if (inlineValue == null) {
                        i++;
                    }
                    upsertHeader(headers, "Referer", v);
                }
                case "-u", "--user" -> {
                    String v = value(option, inlineValue, tokens, i);
                    if (inlineValue == null) {
                        i++;
                    }
                    String encoded = Base64.getEncoder()
                            .encodeToString(v.getBytes(StandardCharsets.UTF_8));
                    upsertHeader(headers, "Authorization", "Basic " + encoded);
                }
                case "--url" -> {
                    String v = value(option, inlineValue, tokens, i);
                    if (inlineValue == null) {
                        i++;
                    }
                    if (url == null) {
                        url = v;
                    }
                }
                default -> {
                    if (DATA_OPTIONS.contains(option)) {
                        String v = value(option, inlineValue, tokens, i);
                        if (inlineValue == null) {
                            i++;
                        }
                        if (v.startsWith("@")) {
                            warnings.add("Reading data from a file (@) is not supported; used the literal text.");
                        }
                        if (body.length() > 0) {
                            body.append('&'); // curl joins multiple --data with &
                        }
                        body.append(v);
                        hasData = true;
                    } else if (IGNORED_WITH_VALUE.contains(option)) {
                        if (inlineValue == null) {
                            i++; // skip its value too
                        }
                    } else if (IGNORED_FLAGS.contains(option)) {
                        noteIgnoredFlag(option, warnings);
                    } else if (token.startsWith("-") && token.length() > 1) {
                        warnings.add("Ignored an unrecognised option: " + option);
                    } else if (url == null) {
                        url = token; // positional argument = the URL
                    } else {
                        warnings.add("Ignored an unexpected extra argument.");
                    }
                }
            }
        }

        if (url == null || url.isBlank()) {
            throw new CurlParseException("Could not determine the request URL from the cURL command.");
        }
        url = url.trim();

        String method;
        if (explicitMethod != null) {
            if (!HTTP_METHODS.contains(explicitMethod)) {
                throw new CurlParseException("Unsupported HTTP method in the cURL command: " + explicitMethod);
            }
            method = explicitMethod;
        } else {
            method = hasData ? "POST" : "GET";
        }

        // Counts only - never values.
        log.info("Imported cURL: method={}, headers={}, cookies={}, hasBody={}",
                method, headers.size(), cookies.size(), body.length() > 0);

        return new ParsedRequestDto(method, url, headers, cookies, body.toString(), warnings);
    }

    // ------------------------------------------------------------------

    /** Return an option's value: the inline {@code --opt=value} part, or the next token. */
    private String value(String option, String inlineValue, List<String> tokens, int i) {
        if (inlineValue != null) {
            return inlineValue;
        }
        if (i + 1 < tokens.size()) {
            return tokens.get(i + 1);
        }
        throw new CurlParseException("The option " + option + " is missing its value.");
    }

    /**
     * Split a header line at the <b>first</b> colon only (values can contain
     * colons, e.g. {@code Authorization: Basic abc:def}). A header literally
     * named {@code Cookie} is redirected into the cookies list so cookie data
     * lives in one place.
     */
    private void applyHeader(String raw, List<HeaderDto> headers, List<CookieDto> cookies,
                             List<String> warnings) {
        int colon = raw.indexOf(':');
        if (colon < 0) {
            if (raw.endsWith(";")) {
                // curl syntax for "send this header with an empty value"
                headers.add(new HeaderDto(raw.substring(0, raw.length() - 1).trim(), ""));
            } else {
                warnings.add("Ignored a header with no ':' separator.");
            }
            return;
        }
        String key = raw.substring(0, colon).trim();
        String value = raw.substring(colon + 1).strip();
        if (key.isEmpty()) {
            warnings.add("Ignored a header with an empty name.");
            return;
        }
        if (key.equalsIgnoreCase("cookie")) {
            parseCookieString(value, cookies);
            return;
        }
        if (key.equalsIgnoreCase("content-length")) {
            warnings.add("Ignored the Content-Length header; it is recalculated automatically.");
            return;
        }
        headers.add(new HeaderDto(key, value));
    }

    /**
     * Parse {@code "session=abc123; theme=dark"} into cookie pairs. Splits pairs
     * on {@code ;} (trimming whitespace) and each pair on the <b>first</b>
     * {@code =} only, so {@code token=abc=def=ghi} -> key {@code token}, value
     * {@code abc=def=ghi}.
     */
    private void parseCookieString(String raw, List<CookieDto> cookies) {
        for (String part : raw.split(";")) {
            String pair = part.trim();
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                cookies.add(new CookieDto(pair, ""));
            } else {
                cookies.add(new CookieDto(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim()));
            }
        }
    }

    /** Add a header, or replace an existing one with the same name (case-insensitive). */
    private void upsertHeader(List<HeaderDto> headers, String name, String value) {
        for (int k = 0; k < headers.size(); k++) {
            if (headers.get(k).key().equalsIgnoreCase(name)) {
                headers.set(k, new HeaderDto(name, value));
                return;
            }
        }
        headers.add(new HeaderDto(name, value));
    }

    private void noteIgnoredFlag(String option, List<String> warnings) {
        switch (option) {
            case "-k", "--insecure" ->
                    warnings.add("Ignored --insecure: this tool always verifies TLS certificates.");
            case "-L", "--location", "--location-trusted" ->
                    warnings.add("Ignored --location: redirects are not followed automatically.");
            default -> {
                // Cosmetic (--compressed, -s, -v, ...): drop silently.
            }
        }
    }
}
