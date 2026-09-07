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
        List<ChainResultDto> results,
        ChainSummaryDto summary
) {
}
