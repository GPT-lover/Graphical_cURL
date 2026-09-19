package com.example.curlgui.dto;

import java.util.List;

/**
 * The request the frontend wants us to perform, e.g.:
 *
 * <pre>
 * {
 *   "method": "POST",
 *   "url": "https://httpbin.org/post",
 *   "headers": [ { "key": "Content-Type", "value": "application/json" } ],
 *   "body": "{\"name\":\"Alex\"}"
 * }
 * </pre>
 *
 * This is a DTO - a shape that only exists to carry data across the REST
 * boundary. It is deliberately separate from any database entity. Spring (via
 * Jackson) creates it from the JSON request body when a controller method
 * parameter is annotated {@code @RequestBody}.
 *
 * {@code headers}, {@code cookies} and {@code body} may be {@code null} - the
 * service handles that. {@code cookies} are combined into a single
 * {@code Cookie: a=1; b=2} header when the request is sent.
 *
 * {@code environmentId} (optional) tells the backend which environment's
 * variables to substitute into {@code {{PLACEHOLDERS}}} before sending. The
 * frontend sends its currently active environment id here. Substitution happens
 * on a copy - this DTO (and therefore Request History) keeps the placeholders.
 *
 * <p>{@code bodyType} selects how the body is sent: {@code "raw"} (default when
 * {@code null} - {@code body} is sent verbatim, unchanged from before multipart
 * support), {@code "multipart"} ({@code multipart} fields are sent as a real
 * {@code multipart/form-data} request built by curl itself - {@code body} is
 * ignored), or {@code "binary"} ({@code body} holds the absolute path to a local
 * file whose bytes are streamed as the request body via curl's
 * {@code --data-binary @file}, never read into this DTO).
 */
public record SendRequestDto(
        String method,
        String url,
        List<HeaderDto> headers,
        List<CookieDto> cookies,
        String body,
        Long environmentId,
        CurlOptionsDto curlOptions,
        String bodyType,
        List<MultipartFieldDto> multipart
) {
    /**
     * Compact form without {@code curlOptions}/{@code bodyType}/{@code multipart}
     * (all default to {@code null}, i.e. a plain raw-body request). Keeps every
     * existing call-site and test that predates transport options / multipart
     * working unchanged.
     */
    public SendRequestDto(String method, String url, List<HeaderDto> headers,
                          List<CookieDto> cookies, String body, Long environmentId) {
        this(method, url, headers, cookies, body, environmentId, null, null, null);
    }

    /** Compact form without {@code bodyType}/{@code multipart} (defaults to raw). */
    public SendRequestDto(String method, String url, List<HeaderDto> headers,
                          List<CookieDto> cookies, String body, Long environmentId,
                          CurlOptionsDto curlOptions) {
        this(method, url, headers, cookies, body, environmentId, curlOptions, null, null);
    }

    public boolean isMultipart() {
        return "multipart".equalsIgnoreCase(bodyType) && multipart != null && !multipart.isEmpty();
    }

    public boolean isBinary() {
        return "binary".equalsIgnoreCase(bodyType);
    }
}
