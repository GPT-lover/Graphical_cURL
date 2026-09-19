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
 *   "body": "{\"name\":\"Alex\"}",
 *   "warnings": [ "Ignored --location: redirects are not followed automatically." ]
 * }
 * </pre>
 *
 * {@code warnings} lists options that were recognised but intentionally dropped
 * (e.g. {@code --compressed}, {@code -k}). It's informational - the import still
 * succeeds. It is usually empty.
 *
 * <p>{@code bodyType} is {@code "raw"} (default/null - the plain {@code body}
 * string, unchanged from before multipart support), {@code "multipart"} (an
 * imported {@code -F}/{@code --form} command - {@code multipart} carries the
 * fields and {@code body} is empty), or {@code "binary"} (an imported {@code
 * --data-binary '@path'} - the file path is carried in {@code body}).
 */
public record ParsedRequestDto(
        String method,
        String url,
        List<HeaderDto> headers,
        List<CookieDto> cookies,
        String body,
        List<String> warnings,
        CurlOptionsDto curlOptions,
        String bodyType,
        List<MultipartFieldDto> multipart
) {
    /** Compact form without {@code curlOptions}/{@code bodyType}/{@code multipart}. */
    public ParsedRequestDto(String method, String url, List<HeaderDto> headers,
                            List<CookieDto> cookies, String body, List<String> warnings) {
        this(method, url, headers, cookies, body, warnings, null, null, null);
    }

    /** Compact form without {@code bodyType}/{@code multipart} (defaults to raw). */
    public ParsedRequestDto(String method, String url, List<HeaderDto> headers,
                            List<CookieDto> cookies, String body, List<String> warnings,
                            CurlOptionsDto curlOptions) {
        this(method, url, headers, cookies, body, warnings, curlOptions, null, null);
    }
}
