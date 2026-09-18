package com.example.curlgui.service;

import java.util.Locale;

/**
 * How the backend invokes Hydra: a native executable ({@link #LOCAL}) or one
 * installed inside a WSL distribution, invoked via {@code wsl.exe}
 * ({@link #WSL}). Shared by {@link HydraSettingsService} (persists/validates
 * the setting) and {@link HydraAttackService} (branches its behaviour on it).
 */
enum HydraExecutionMode {
    LOCAL, WSL;

    /** Missing/blank -&gt; {@link #LOCAL}, so existing local-executable setups keep working unchanged. */
    static HydraExecutionMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return LOCAL;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Execution mode must be \"LOCAL\" or \"WSL\".");
        }
    }
}
