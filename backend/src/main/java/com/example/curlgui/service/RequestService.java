package com.example.curlgui.service;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.curlgui.dto.CurlOptionsDto;
import com.example.curlgui.dto.MultipartFieldDto;
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
    private final DynamicVariableResolver dynamicResolver;

    public RequestService(CurlProcessExecutor curlExecutor,
                          RequestHistoryService historyService,
                          EnvironmentVariableService environmentVariableService,
                          EnvironmentVariableResolver variableResolver,
                          DynamicVariableResolver dynamicResolver) {
        this.curlExecutor = curlExecutor;
        this.historyService = historyService;
        this.environmentVariableService = environmentVariableService;
        this.variableResolver = variableResolver;
        this.dynamicResolver = dynamicResolver;
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
     * Send an <b>already-resolved</b> request: no environment {{variable}}
     * substitution and no History. Dynamic {@code {{random(N)}}} templates are
     * still resolved, per send, because their whole point is a fresh value each
     * time. Shared by {@link #execute} (the normal Send) and the run-multiple
     * loop, so both use the same curl-execution path, timeouts and response
     * handling. Runs quietly (no per-request "Proxying..." log line).
     */
    public SendResponseDto executeResolved(SendRequestDto resolved) {
        return executeResolved(resolved, false);
    }

    private SendResponseDto executeResolved(SendRequestDto request, boolean logProxyLine) {
        // Dynamic {{random(N)}} / {{increment(N)}} templates are resolved HERE -
        // once per actual send, on a copy - so every loop iteration, every
        // parallel worker and every chain dispatch gets its own freshly
        // generated random value and the right increment value, while the
        // caller's request keeps its templates for the next iteration.
        // IterationContext.current() is 1 for a plain Send; RequestLoopRunner and
        // ChainRunner set it to the real iteration number around this call.
        SendRequestDto resolved = dynamicResolver.resolveRequest(request, IterationContext.current());
        String method = normaliseMethod(resolved.method());
        URI uri = parseAndValidateUrl(resolved.url());
        String body = resolved.body() == null ? "" : resolved.body();
        CurlOptionsDto options = CurlOptionsDto.orNone(resolved.curlOptions());

        // Validate local files BEFORE curl is touched, so a missing/moved file
        // (the saved request may be pointing at a path that no longer exists)
        // fails fast with a clear message instead of silently sending an empty
        // field or a broken request. Checked against bodyType directly (not the
        // DTO's isMultipart()/isBinary() convenience methods, which treat an
        // empty field list as "not multipart") so an explicitly-multipart request
        // with no fields is rejected rather than silently sent with no body.
        String bodyType = resolved.bodyType();
        if ("multipart".equalsIgnoreCase(bodyType)) {
            validateMultipartFields(resolved.multipart());
        } else if ("binary".equalsIgnoreCase(bodyType)) {
            validateLocalFile(body, "the request body file");
        }

        if (logProxyLine) {
            // Log host only - never the full URL (query strings can carry
            // tokens), never headers, never the body.
            log.info("Proxying {} request to host \"{}\"", method, uri.getHost());
        }

        List<String> warnings = new ArrayList<>();
        CurlProcessExecutor.Result result = curlExecutor.execute(
                method, uri, body, resolved.headers(), resolved.cookies(), options, warnings,
                resolved.bodyType(), resolved.multipart());

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

    /**
     * Every field needs a name, curl's {@code -F name=value} syntax can't
     * represent a name containing {@code '='}, and every file field's local path
     * must exist and be readable - checked here, before curl is invoked, so a
     * missing/moved/renamed file (most likely: a saved request whose file moved
     * since it was saved) fails with one clear message naming the field, instead
     * of curl failing generically or the field silently going out empty.
     */
    private void validateMultipartFields(List<MultipartFieldDto> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new InvalidRequestException(
                    "This multipart/form-data request has no fields; add at least one.");
        }
        for (MultipartFieldDto field : fields) {
            if (field == null || field.name() == null || field.name().isBlank()) {
                throw new InvalidRequestException("Every multipart field needs a name.");
            }
            if (field.name().contains("=")) {
                throw new InvalidRequestException(
                        "Multipart field name \"" + field.name() + "\" cannot contain '='.");
            }
            if (field.isFile()) {
                String what = "field \"" + field.name() + "\"";
                validateLocalFile(field.value(), what);
                // curl's -F reads an unquoted file path up to the first ';' or
                // ',' (its own parameter/multi-file separators) - see
                // MultipartFormValue's class docs for why the execution path
                // can't safely use curl's quoted-path escape hatch here.
                if (field.value().indexOf(';') >= 0 || field.value().indexOf(',') >= 0) {
                    throw new InvalidRequestException(
                            "The file path for " + what + " contains ';' or ',', which cannot be "
                                    + "used in a multipart file upload: \"" + field.value() + "\".");
                }
            }
        }
    }

    /** See {@link #validateMultipartFields}; also used for the "Binary File" body type. */
    private void validateLocalFile(String path, String what) {
        if (path == null || path.isBlank()) {
            throw new InvalidRequestException("Choose a file for " + what + " before sending.");
        }
        Path p;
        try {
            p = Path.of(path);
        } catch (InvalidPathException ex) {
            throw new InvalidRequestException("The file path for " + what + " is not valid: " + path);
        }
        if (!Files.exists(p)) {
            throw new InvalidRequestException(
                    "The file for " + what + " no longer exists at \"" + path
                            + "\". Choose a replacement file.");
        }
        if (!Files.isRegularFile(p)) {
            throw new InvalidRequestException("The path for " + what + " is not a file: " + path);
        }
        if (!Files.isReadable(p)) {
            throw new InvalidRequestException(
                    "The file for " + what + " could not be read (check permissions): " + path);
        }
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
