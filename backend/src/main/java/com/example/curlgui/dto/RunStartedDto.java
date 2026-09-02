package com.example.curlgui.dto;

/** Response of {@code POST /api/requests/run-multiple}: poll status with this id. */
public record RunStartedDto(String runId) {
}
