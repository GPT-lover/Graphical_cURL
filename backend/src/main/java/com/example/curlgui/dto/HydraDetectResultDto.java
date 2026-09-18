package com.example.curlgui.dto;

/** Response of {@code GET /api/hydra/detect} - the result of probing an executable with {@code -h}. */
public record HydraDetectResultDto(boolean available, String message) {
}
