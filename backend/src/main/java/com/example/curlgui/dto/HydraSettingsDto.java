package com.example.curlgui.dto;

/**
 * The Hydra execution settings.
 *
 * @param executionMode  "LOCAL" | "WSL"
 * @param executablePath used when {@code executionMode == "LOCAL"} - path to a
 *                       native Hydra executable. Empty string means "not
 *                       configured".
 * @param wslDistro      used when {@code executionMode == "WSL"} - the WSL
 *                       distribution name (e.g. "kali-linux").
 * @param wslHydraPath   used when {@code executionMode == "WSL"} - the path to
 *                       Hydra *inside* that distribution (e.g. "/usr/bin/hydra").
 */
public record HydraSettingsDto(
        String executionMode,
        String executablePath,
        String wslDistro,
        String wslHydraPath
) {
}
