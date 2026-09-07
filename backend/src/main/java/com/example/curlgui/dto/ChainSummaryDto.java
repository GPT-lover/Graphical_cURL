package com.example.curlgui.dto;

/**
 * Final summary of a chain run (present once status is DONE or STOPPED).
 *
 * <p>{@code averageDurationMs} is the mean duration of individual requests that
 * got a result; {@code elapsedMs} is the wall-clock time for the whole chain run
 * - since requests overlap, this is typically far less than the sum of every
 * individual duration.
 */
public record ChainSummaryDto(
        int totalIterations,
        int chainLength,
        int totalDispatches,
        int completed,
        int successful,
        int redirects,
        int failed,
        long averageDurationMs,
        long elapsedMs,
        boolean stopped
) {
}
