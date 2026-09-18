package com.example.curlgui.dto;

import java.util.List;

/**
 * Response of {@code GET /api/hydra/attacks/{attackId}?offset=N}.
 *
 * <p>{@code output} contains only the lines after {@code offset} (the client
 * passes how many it already has), so a long-running attack isn't re-sent in
 * full on every poll. {@code exitCode} and {@code errorMessage} are null while
 * {@code status == "RUNNING"}.
 */
public record HydraAttackStatusDto(
        String status, // RUNNING | DONE | STOPPED | ERROR
        List<String> output,
        Integer exitCode,
        String errorMessage
) {
}
