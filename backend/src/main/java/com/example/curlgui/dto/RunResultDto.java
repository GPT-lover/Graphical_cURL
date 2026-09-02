package com.example.curlgui.dto;

/**
 * One iteration's outcome - metadata only, never a response body or any header.
 *
 * @param run            1-based run number (results may complete out of order)
 * @param status         HTTP status code, or {@code null} for a network error
 * @param durationMs     request duration, or {@code null} for a network error
 * @param error          short label such as "Network Error", or {@code null}
 * @param classification "SUCCESS" (2xx) / "REDIRECT" (3xx) / "FAILED" (4xx, 5xx, network)
 */
public record RunResultDto(
        int run,
        Integer status,
        Long durationMs,
        String error,
        String classification
) {
}
