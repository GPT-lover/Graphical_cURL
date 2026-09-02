package com.example.curlgui.dto;

import java.util.List;
import java.util.Map;

/**
 * What we send back to the frontend after performing the request:
 *
 * <pre>
 * {
 *   "statusCode": 200,
 *   "headers": { "content-type": "application/json" },
 *   "body": "...",
 *   "durationMs": 123,
 *   "warnings": []
 * }
 * </pre>
 *
 * Important: a 404 or 500 <em>from the target server</em> is still a normal,
 * successful result of "we performed your request" - it comes back here with
 * {@code statusCode: 404}, HTTP 200 from our own API. Only failures in <em>our</em>
 * backend (bad URL, DNS failure, timeout) produce an {@link ErrorResponseDto}.
 *
 * {@code warnings} lists non-fatal problems, e.g. a header we had to skip because
 * {@code HttpClient} forbids setting it. Usually empty.
 */
public record SendResponseDto(
        int statusCode,
        Map<String, String> headers,
        String body,
        long durationMs,
        List<String> warnings
) {
}
