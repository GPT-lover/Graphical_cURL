package com.example.curlgui.dto;

import java.util.List;

/**
 * One history entry as returned by {@code GET /api/history}.
 *
 * <pre>
 * {
 *   "id": 12,
 *   "method": "POST",
 *   "url": "https://example.com/api/rate",
 *   "headers": [ { "key": "Content-Type", "value": "application/json" } ],
 *   "body": "{\"rating\":7}",
 *   "statusCode": 200,
 *   "durationMs": 183,
 *   "createdAt": "2026-09-02T18:30:00.123Z"
 * }
 * </pre>
 *
 * {@code headers} never contains sensitive headers - they were removed before
 * the row was stored - and there is no {@code cookies} field because cookies are
 * not persisted.
 */
public record RequestHistoryDto(
        Long id,
        String method,
        String url,
        List<HeaderDto> headers,
        String body,
        int statusCode,
        long durationMs,
        String createdAt
) {
}
