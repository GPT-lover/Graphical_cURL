package com.example.curlgui.dto;

/** Body of {@code PUT /api/hydra/settings}. Same shape as {@link HydraSettingsDto}. */
public record UpdateHydraSettingsDto(
        String executionMode,
        String executablePath,
        String wslDistro,
        String wslHydraPath
) {
}
