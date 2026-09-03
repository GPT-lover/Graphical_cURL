package com.example.curlgui.service;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.curlgui.dto.CurlOptionsDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.dto.SendResponseDto;

/**
 * Performs the outgoing HTTP request described by a {@link SendRequestDto} by
 * running the real {@code curl} executable (via {@link CurlProcessExecutor}) and
 * maps the result into a {@link SendResponseDto}.
 *
 * <p>Execution moved from {@code java.net.http.HttpClient} to {@code curl}
 * because some sites behind WAF / bot-fingerprinting (e.g. Vercel's security
 * checkpoint) rejected the JDK client's TLS / HTTP-2 fingerprint with a 429
 * challenge while accepting the byte-identical request from CLI curl. This class
 * still never runs a shell and never disables TLS verification on its own -
 * {@code -k} is honoured only when it was in the imported command, with a
 * warning.
 *
 * <p>Responsibilities that did <em>not</em> move: {@code {{variable}}}
 * resolution (on a temporary copy, so History keeps placeholders), method / URL
 * validation, and recording a sanitised History row.
 */
@Service
public class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);

    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private final CurlProcessExecutor curlExecutor;
    private final RequestHistoryService historyService;
    private final EnvironmentVariableService environmentVariableService;
    private final EnvironmentVariableResolver variableResolver;

    public RequestService(CurlProcessExecutor curlExecutor,
                          RequestHistoryService historyService,
                          EnvironmentVariableService environmentVariableService,
                          EnvironmentVariableResolver variableResolver) {
        this.curlExecutor = curlExecutor;
        this.historyService = historyService;
        this.environmentVariableService = environmentVariableService;
        this.variableResolver = variableResolver;
    }

    /**
     * Resolve {@code {{variables}}}, validate, run the request through curl, and
     * convert the response.
     *
     * <p>Substitution happens on a <b>temporary copy</b> ({@code resolved}). The
     * original {@code dto} keeps its placeholders and is what Request History
     * records, so no resolved secret is ever persisted. If any placeholder can't
     * be resolved, {@link UnresolvedVariableException} is thrown here - before
     * curl is touched - and nothing is sent.
     *
     * <p>Throws {@link InvalidRequestException} for bad input and
     * {@link RequestExecutionException} for a failure to <em>perform</em> the
     * request (curl missing / failed to start / timed out / non-zero exit) -
     * both handled by the controller. A completed HTTP response, including
     * 404 / 429 / 500, is returned normally.
     */
    public SendResponseDto execute(SendRequestDto dto) {
        if (dto == null) {
            throw new InvalidRequestException("Request body is missing");
        }

        Map<String, String> variables =
                environmentVariableService.variablesFor(dto.environmentId());
        SendRequestDto resolved = variableResolver.resolveRequest(dto, variables);

        SendResponseDto response = executeResolved(resolved, true);

        // Persist a (sanitised) history row from the ORIGINAL dto (placeholders
        // intact). This must never break a successful request, so any failure
        // here is swallowed and logged without values.
        try {
            historyService.record(dto, response);
        } catch (Exception ex) {
            log.warn("Request succeeded but its history entry could not be saved: {}",
                    ex.getClass().getSimpleName());
        }

        return response;
    }

    /**
     * Send an <b>already-resolved</b> request: no {{variable}} substitution and no
     * History. Shared by {@link #execute} (the normal Send) and the run-multiple
     * loop, so both use the same curl-execution path, timeouts and response
     * handling. Runs quietly (no per-request "Proxying..." log line).
     */
    public SendResponseDto executeResolved(SendRequestDto resolved) {
        return executeResolved(resolved, false);
    }

    private SendResponseDto executeResolved(SendRequestDto resolved, boolean logProxyLine) {
        String method = normaliseMethod(resolved.method());
        URI uri = parseAndValidateUrl(resolved.url());
        String body = resolved.body() == null ? "" : resolved.body();
        CurlOptionsDto options = CurlOptionsDto.orNone(resolved.curlOptions());

        if (logProxyLine) {
            // Log host only - never the full URL (query strings can carry
            // tokens), never headers, never the body.
            log.info("Proxying {} request to host \"{}\"", method, uri.getHost());
        }

        List<String> warnings = new ArrayList<>();
        CurlProcessExecutor.Result result = curlExecutor.execute(
                method, uri, body, resolved.headers(), resolved.cookies(), options, warnings);

        Charset charset = charsetFromContentType(firstHeader(result.headers(), "content-type"));
        String decodedBody = new String(result.body(), charset);

        return new SendResponseDto(
                result.statusCode(), result.headers(), decodedBody,
                result.durationMs(), warnings);
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
     * read the backend's own filesystem or reach non-HTTP services via curl.
     */
    // package-private so RunMultipleService can fail fast on a bad resolved URL
    URI parseAndValidateUrl(String rawUrl) {
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
    // Response mapping helpers
    // ------------------------------------------------------------------

    /** Case-insensitive lookup in the flattened response-header map. */
    private static String firstHeader(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
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
