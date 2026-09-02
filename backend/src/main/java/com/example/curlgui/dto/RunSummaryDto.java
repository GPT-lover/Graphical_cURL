package com.example.curlgui.dto;

/**
 * Final summary of a loop (present once status is DONE or STOPPED).
 *
 * <p>{@code averageDurationMs} is the mean duration of individual requests (that
 * got a response); {@code elapsedMs} is the wall-clock time for the whole loop -
 * for PARALLEL mode these differ a lot.
 */
public record RunSummaryDto(
        int total,
        int completed,
        int successful,
        int redirects,
        int failed,
        long averageDurationMs,
        long elapsedMs,
        String mode,
        boolean stopped
) {
}
