package com.example.curlgui.dto;

import java.util.List;

/**
 * Response of {@code GET /api/requests/run-chain/{chainId}?offset=N}.
 *
 * <p>Unlike the run-multiple loop's append-only results, a chain slot changes
 * <em>in place</em>: it first appears as "dispatched" (no response yet) and is
 * updated again once its result arrives. {@code offset} is therefore not a count
 * of finished requests but of <em>change events</em> the client has already
 * consumed (a slot can appear more than once in the returned {@code results} as
 * it moves from dispatched to completed) - see {@code ChainState}. {@code
 * summary} is {@code null} while {@code status == "RUNNING"}.
 *
 * <p>{@code cooldownMs} echoes the configured pause between loop iterations (0 =
 * none); {@code coolingDown} is {@code true} only while the run is currently
 * waiting out that pause between two iterations, so the UI can show "waiting…".
 */
public record ChainStatusDto(
        String status,   // RUNNING | DONE | STOPPED
        int totalIterations,
        int chainLength,
        int totalDispatches,
        int dispatched,
        int completed,
        int successful,
        int redirects,
        int failed,
        long cooldownMs,
        boolean coolingDown,
        List<ChainResultDto> results,
        ChainSummaryDto summary
) {
}
