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
 * {@code headers} and {@code body} may be {@code null} - the service handles that.
 */
public record SendRequestDto(
        String method,
        String url,
        List<HeaderDto> headers,
        String body
) {
}
