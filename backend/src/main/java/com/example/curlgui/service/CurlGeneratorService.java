package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.SendRequestDto;

/**
 * Generates a POSIX-shell cURL command from a request the GUI built - the
 * inverse of {@link CurlParserService}.
 *
 * <p>Pure text generation. It never executes anything: no shell, no
 * {@code Runtime.exec}, no {@code ProcessBuilder}. Nothing is logged - the
 * output can contain cookies, {@code Authorization} headers and tokens.
 *
 * <p>Reuses {@link ShellQuote} for escaping and {@link CookieHeader} for the
 * "Cookies section vs. a manually typed Cookie header" policy, so an imported
 * request, a sent request and an exported request all agree on cookies.
 */
@Service
public class CurlGeneratorService {

    /** Join options onto their own indented, backslash-continued line. */
    private static final String LINE_JOIN = " \\\n  ";

    private static final Set<String> HTTP_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    public String generate(SendRequestDto dto) {
        if (dto == null) {
            throw new InvalidRequestException("There is no request to export.");
        }

        String url = dto.url() == null ? "" : dto.url().trim();
        if (url.isEmpty()) {
            throw new InvalidRequestException("Enter a URL before exporting to cURL.");
        }

        String method = dto.method() == null ? "GET" : dto.method().trim().toUpperCase(Locale.ROOT);
        if (!HTTP_METHODS.contains(method)) {
            throw new InvalidRequestException("Unsupported HTTP method: " + dto.method());
        }

        String body = dto.body() == null ? "" : dto.body();
        boolean hasBody = !body.isEmpty();

        List<String> parts = new ArrayList<>();
        parts.add("curl " + ShellQuote.single(url));

        // Method: cURL defaults to GET, and to POST when a body is present. Emit
        // -X for every other method, and also for GET-with-a-body so the request
        // is not silently turned into a POST.
        if (!("GET".equals(method) && !hasBody)) {
            parts.add("-X " + method);
        }

        // Headers: order, duplicates and capitalisation are preserved exactly.
        // A header literally named "Cookie" (any case) is left out here - cookies
        // are emitted once as -b below.
        List<HeaderDto> headers = dto.headers() == null ? List.of() : dto.headers();
        for (HeaderDto header : headers) {
            if (header == null) {
                continue;
            }
            String name = header.key() == null ? "" : header.key().trim();
            if (name.isEmpty() || name.equalsIgnoreCase("Cookie")) {
                continue;
            }
            String value = header.value() == null ? "" : header.value();
            parts.add("-H " + ShellQuote.single(name + ": " + value));
        }

        // Cookies: a single  -b 'a=1; b=2'. Same policy as the request executor -
        // the Cookies section wins; otherwise a manually typed Cookie header is
        // used; blank rows are dropped; duplicate names keep the last value.
        String manualCookieHeader = CookieHeader.extractManualCookieHeader(headers);
        CookieHeader.Result cookies = CookieHeader.resolve(dto.cookies(), manualCookieHeader);
        if (cookies.value() != null) {
            parts.add("-b " + ShellQuote.single(cookies.value()));
        }

        // Body: verbatim, never reformatted or pretty-printed.
        if (hasBody) {
            parts.add("--data-raw " + ShellQuote.single(body));
        }

        return String.join(LINE_JOIN, parts);
    }
}
