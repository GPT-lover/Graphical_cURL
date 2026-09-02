package com.example.curlgui.dto;

/**
 * One variable. Values are only returned by the environment-management endpoints
 * ({@code GET /api/environments/{id}} and the variable create/update responses) -
 * never by unrelated endpoints.
 */
public record EnvironmentVariableDto(Long id, String key, String value) {
}
