package com.example.curlgui.dto;

import java.util.List;

/**
 * Response of {@code GET /api/requests/run-multiple/{runId}?offset=N}.
 *
 * <p>{@code results} contains only the entries after {@code offset} (the client
 * passes how many it already has), so a 5000-run loop isn't re-sent on every
 * poll. {@code summary} is {@code null} while {@code status == "RUNNING"}.
 *
 * <p>{@code waiting} is {@code true} only while the loop is currently pausing
 * before its next iteration; {@code currentWaitMs} is that pause's actual
 * duration (varies per iteration under JITTER/WINDOW pacing, so the UI shows
 * the real number rather than a static configured value).
 */
public record RunStatusDto(
        String status,   // RUNNING | DONE | STOPPED
        String mode,     // SEQUENTIAL | PARALLEL
        int total,
        int completed,
        int successful,
        int redirects,
        int failed,
        boolean waiting,
        long currentWaitMs,
        List<RunResultDto> results,
        RunSummaryDto summary
) {
}
