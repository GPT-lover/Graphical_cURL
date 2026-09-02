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
 *   "body": "{\"name\":\"William\"}"
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
 */
public record SendRequestDto(
        String method,
        String url,
        List<HeaderDto> headers,
        List<CookieDto> cookies,
        String body,
        Long environmentId
) {
}
