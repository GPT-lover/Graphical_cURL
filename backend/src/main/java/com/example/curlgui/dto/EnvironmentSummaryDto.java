package com.example.curlgui.dto;

/** An environment without its variables - for {@code GET /api/environments}. */
public record EnvironmentSummaryDto(Long id, String name) {
}
