package com.example.curlgui.dto;

/**
 * Returned (with a 4xx/5xx status from <em>our</em> API) when the backend itself
 * could not carry out the request - invalid URL, unsupported method, DNS
 * failure, connection refused, timeout, etc.
 *
 * <pre>
 * { "error": "Could not connect to the target server", "message": "Connection refused" }
 * </pre>
 *
 * {@code error}   - short, safe-to-display summary.
 * {@code message} - a bit more detail (the underlying exception's message). Never
 *                   a Java stack trace.
 */
public record ErrorResponseDto(String error, String message) {
}
