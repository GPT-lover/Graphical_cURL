package com.example.curlgui.dto;

/**
 * The current state of one dispatched slot in a chain run - metadata only, never
 * a response body or any header.
 *
 * <p>A slot is identified by {@code (iteration, requestIndex)}, not by arrival
 * order: the chain dispatches requests strictly in order, but their responses
 * can complete in any order, so the frontend must key its results table by this
 * pair rather than by the order results are received.
 *
 * @param iteration      1-based loop iteration
 * @param requestIndex   0-based position of this request within the chain
 * @param status         HTTP status code, or {@code null} while awaiting a
 *                        response / on a network error
 * @param durationMs     request duration, or {@code null} until a result exists
 * @param error          short label such as "Network Error", or {@code null}
 * @param classification {@code null} while the request has been dispatched but
 *                        no result has arrived yet; otherwise "SUCCESS" (2xx) /
 *                        "REDIRECT" (3xx) / "FAILED" (4xx, 5xx, network error)
 */
public record ChainResultDto(
        int iteration,
        int requestIndex,
        Integer status,
        Long durationMs,
        String error,
        String classification
) {
}
