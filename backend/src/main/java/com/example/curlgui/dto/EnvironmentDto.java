package com.example.curlgui.dto;

import java.util.List;

/** One environment with its variables - for {@code GET /api/environments/{id}}. */
public record EnvironmentDto(Long id, String name, List<EnvironmentVariableDto> variables) {
}
