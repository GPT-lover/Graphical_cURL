package com.example.curlgui.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.curlgui.service.EnvironmentService;

/**
 * Ensures the "Default" environment exists on startup so the user always has an
 * active environment to send with. Idempotent - restarting never creates
 * duplicates. Mirrors {@link DefaultCollectionInitializer}.
 */
@Component
public class DefaultEnvironmentInitializer {

    private final EnvironmentService environmentService;

    public DefaultEnvironmentInitializer(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createDefaultEnvironmentIfMissing() {
        environmentService.ensureDefaultEnvironment();
    }
}
