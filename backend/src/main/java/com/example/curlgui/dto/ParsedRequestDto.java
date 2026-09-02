package com.example.curlgui.dto;

import java.util.List;

/**
 * Result of parsing a cURL command - everything the frontend needs to populate
 * the request editor:
 *
 * <pre>
 * {
 *   "method": "POST",
 *   "url": "https://example.com/api/user",
 *   "headers":  [ { "key": "accept", "value": "application/json" } ],
 *   "cookies":  [ { "key": "session", "value": "xyz789" } ],
 *   "body": "{\"name\":\"William\"}",
 *   "warnings": [ "Ignored --location: redirects are not followed automatically." ]
 * }
 * </pre>
 *
 * {@code warnings} lists options that were recognised but intentionally dropped
 * (e.g. {@code --compressed}, {@code -k}). It's informational - the import still
 * succeeds. It is usually empty.
 */
public record ParsedRequestDto(
        String method,
        String url,
        List<HeaderDto> headers,
        List<CookieDto> cookies,
        String body,
        List<String> warnings
) {
}
