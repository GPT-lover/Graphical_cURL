package com.example.curlgui.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.curlgui.dto.CookieDto;
import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.dto.SendResponseDto;

/**
 * Performs the outgoing HTTP request described by a {@link SendRequestDto} using
 * Java's built-in {@link HttpClient}, and maps the result into a
 * {@link SendResponseDto}.
 *
 * <p>{@code @Service} marks this as a Spring-managed component that holds
 * business logic. Spring creates one instance and injects it into the
 * controller. It gets the shared {@link HttpClient} through its constructor
 * (constructor injection - the preferred style: dependencies are explicit and
 * the object can't exist half-built).
 *
 * <p>This class never runs a shell, never touches {@code Runtime.exec}, and never
 * disables TLS. It only validates input and calls {@code HttpClient}.
 */
@Service
public class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);

    /** Overall cap on a single request/response exchange. See explanation below. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private final HttpClient httpClient;

    public RequestService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Validate the DTO, build an {@link HttpRequest}, send it, and convert the
     * response. Throws {@link InvalidRequestException} for bad input and
     * {@link RequestExecutionException} for network failures - both handled by
     * the controller.
     */
    public SendResponseDto execute(SendRequestDto dto) {
        if (dto == null) {
            throw new InvalidRequestException("Request body is missing");
        }

        String method = normaliseMethod(dto.method());
        URI uri = parseAndValidateUrl(dto.url());
        String body = dto.body() == null ? "" : dto.body();

        // Log host only - never the full URL (query strings can carry tokens),
        // never headers, never the body.
        log.info("Proxying {} request to host \"{}\"", method, uri.getHost());

        List<String> warnings = new ArrayList<>();
        HttpRequest httpRequest =
                buildHttpRequest(method, uri, body, dto.headers(), dto.cookies(), warnings);

        long startNanos = System.nanoTime();
        HttpResponse<byte[]> httpResponse = send(httpRequest, uri);
        long durationMs = Math.round((System.nanoTime() - startNanos) / 1_000_000.0);

        return toResponseDto(httpResponse, durationMs, warnings);
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private String normaliseMethod(String rawMethod) {
        String method = rawMethod == null ? "" : rawMethod.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_METHODS.contains(method)) {
            throw new InvalidRequestException(
                    "Unsupported HTTP method: \"" + rawMethod + "\". Allowed: " + ALLOWED_METHODS);
        }
        return method;
    }

    /**
     * Reasonable URL validation: must parse, must be absolute, must be http/https,
     * must have a host. We do NOT block private / loopback addresses - hitting
     * {@code http://localhost:3000} is a normal thing to do with a dev HTTP tool.
     * Rejecting {@code file:}, {@code ftp:}, etc. keeps this from being able to
     * read the backend's own filesystem.
     */
    private URI parseAndValidateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidRequestException("URL must not be empty");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("URL is not valid: " + rawUrl);
        }
        if (!uri.isAbsolute() || uri.getScheme() == null) {
            throw new InvalidRequestException(
                    "URL must be absolute and start with http:// or https://");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new InvalidRequestException(
                    "Only http and https URLs are supported (got \"" + scheme + "\")");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidRequestException("URL must include a host, e.g. https://example.com/path");
        }
        return uri;
    }

    // ------------------------------------------------------------------
    // Build the outgoing request
    // ------------------------------------------------------------------

    private HttpRequest buildHttpRequest(String method, URI uri, String body,
                                         List<HeaderDto> headers, List<CookieDto> cookies,
                                         List<String> warnings) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT);

        // Body: send one only if the user actually typed something. This works
        // for every method - GET/HEAD/OPTIONS simply get BodyPublishers.noBody().
        HttpRequest.BodyPublisher bodyPublisher = body.isEmpty()
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        builder.method(method, bodyPublisher);

        // addHeaders returns any header the user typed literally named "Cookie".
        String manualCookieHeader = addHeaders(builder, headers, warnings);
        applyCookies(builder, cookies, manualCookieHeader, warnings);

        return builder.build();
    }

    /**
     * Copy the user's headers onto the request. Blank rows are skipped. A header
     * literally named {@code Cookie} is <em>not</em> added here - it's returned
     * so {@link #applyCookies} can decide between it and the Cookies section.
     *
     * <p>{@code HttpClient} throws {@link IllegalArgumentException} for headers it
     * refuses to let callers set (e.g. {@code Host}, {@code Content-Length},
     * {@code Connection}) or for names containing illegal characters - we catch
     * that, skip the header, and record a warning rather than failing the whole
     * request.
     *
     * @return the value of a manually entered {@code Cookie} header, or {@code null}
     */
    private String addHeaders(HttpRequest.Builder builder, List<HeaderDto> headers,
                              List<String> warnings) {
        String manualCookieHeader = null;
        if (headers == null) {
            return null;
        }
        for (HeaderDto header : headers) {
            if (header == null) {
                continue;
            }
            String name = header.key() == null ? "" : header.key().trim();
            if (name.isEmpty()) {
                continue; // half-typed row in the editor
            }
            String value = header.value() == null ? "" : header.value();
            if (name.equalsIgnoreCase("Cookie")) {
                manualCookieHeader = value; // handled by applyCookies()
                continue;
            }
            try {
                builder.header(name, value);
            } catch (IllegalArgumentException ex) {
                warnings.add("Skipped header \"" + name + "\" - it is reserved or malformed "
                        + "and cannot be set by this client.");
            }
        }
        return manualCookieHeader;
    }

    /**
     * Combine the Cookies section into a single {@code Cookie: a=1; b=2} header.
     *
     * <p>Conflict rule (documented behaviour): <b>the Cookies section wins.</b>
     * If it has any entries, they become the one and only {@code Cookie} header,
     * replacing anything the user typed as a literal {@code Cookie} header (with
     * a warning). If the Cookies section is empty, a manually typed {@code Cookie}
     * header is sent unchanged. Either way there is at most one {@code Cookie}
     * header - never duplicates.
     */
    private void applyCookies(HttpRequest.Builder builder, List<CookieDto> cookies,
                              String manualCookieHeader, List<String> warnings) {
        String fromSection = buildCookieHeader(cookies);

        String cookieHeader;
        if (fromSection != null) {
            cookieHeader = fromSection;
            if (manualCookieHeader != null && !manualCookieHeader.isBlank()) {
                warnings.add("Your manually entered \"Cookie\" header was replaced by the Cookies section.");
            }
        } else if (manualCookieHeader != null && !manualCookieHeader.isBlank()) {
            cookieHeader = manualCookieHeader;
        } else {
            return; // no cookies at all
        }

        try {
            builder.header("Cookie", cookieHeader);
        } catch (IllegalArgumentException ex) {
            warnings.add("Could not set the Cookie header on the outgoing request.");
        }
    }

    /** Build {@code "a=1; b=2"} from the cookie rows, skipping blank names. Null if none. */
    private String buildCookieHeader(List<CookieDto> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (CookieDto cookie : cookies) {
            if (cookie == null) {
                continue;
            }
            String name = cookie.key() == null ? "" : cookie.key().trim();
            if (name.isEmpty()) {
                continue;
            }
            String value = cookie.value() == null ? "" : cookie.value().trim();
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(name).append('=').append(value);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // ------------------------------------------------------------------
    // Send + map the response
    // ------------------------------------------------------------------

    private HttpResponse<byte[]> send(HttpRequest request, URI uri) {
        try {
            // ofByteArray so we can decode the body with whatever charset the
            // response declares (defaulting to UTF-8).
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpConnectTimeoutException ex) {
            throw fail(uri, ex, "Timed out establishing a connection to the target server");
        } catch (HttpTimeoutException ex) {
            throw fail(uri, ex, "The target server did not respond within " + REQUEST_TIMEOUT.toSeconds() + "s");
        } catch (UnknownHostException ex) {
            throw fail(uri, ex, "Could not resolve host \"" + uri.getHost() + "\"");
        } catch (ConnectException ex) {
            throw fail(uri, ex, "Could not connect to the target server");
        } catch (IOException ex) {
            throw fail(uri, ex, "Network error while contacting the target server");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw fail(uri, ex, "The request was interrupted before it completed");
        }
    }

    private RequestExecutionException fail(URI uri, Exception cause, String friendlyMessage) {
        // Log the exception type + message only - not a stack trace, not headers.
        log.warn("Request to \"{}\" failed: {}: {}",
                uri.getHost(), cause.getClass().getSimpleName(), cause.getMessage());
        return new RequestExecutionException(friendlyMessage, cause.getMessage());
    }

    private SendResponseDto toResponseDto(HttpResponse<byte[]> response, long durationMs,
                                          List<String> warnings) {
        // Flatten multi-valued headers ("a", "b") into "a, b" for easy display.
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) ->
                responseHeaders.put(name, String.join(", ", values)));

        Charset charset = charsetFromContentType(
                response.headers().firstValue("content-type").orElse(null));
        String body = new String(response.body(), charset);

        return new SendResponseDto(response.statusCode(), responseHeaders, body, durationMs, warnings);
    }

    /** Pull {@code charset=...} out of a Content-Type header; default UTF-8. */
    private Charset charsetFromContentType(String contentType) {
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String trimmed = part.trim();
                if (trimmed.regionMatches(true, 0, "charset=", 0, "charset=".length())) {
                    String name = trimmed.substring("charset=".length()).trim().replace("\"", "");
                    try {
                        return Charset.forName(name);
                    } catch (RuntimeException ignored) {
                        // Unknown/illegal charset name - fall back to UTF-8.
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
