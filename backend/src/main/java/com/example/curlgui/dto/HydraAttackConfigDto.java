package com.example.curlgui.dto;

/**
 * Body of {@code POST /api/hydra/attacks} - one {@code http-post-form} /
 * {@code https-post-form} Hydra attack, built from structured fields rather
 * than a raw command string. See
 * {@link com.example.curlgui.service.HydraCommandBuilder} for how this becomes
 * the argument list handed to {@code ProcessBuilder}.
 */
public record HydraAttackConfigDto(
        String host,
        Integer port,
        String protocol,          // "http" | "https"
        String username,
        String wordlistPath,
        String path,               // e.g. "/login"
        String formParams,         // e.g. "username=^USER^&password=^PASS^"
        String cookies,            // optional Cookie header value, e.g. "session=abc123; csrftoken=xyz789"
        String failureCondition    // Hydra's F= condition, e.g. "incorrect"
) {
}
