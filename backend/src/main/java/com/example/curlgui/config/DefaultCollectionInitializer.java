package com.example.curlgui.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.curlgui.service.CollectionService;

/**
 * Makes sure the default "My Requests" collection exists so the user can save a
 * request the very first time they run the app.
 *
 * <p>Runs once, after the context is fully started ({@code ApplicationReadyEvent}
 * fires after JPA and the web server are up).
 * {@link CollectionService#ensureDefaultCollection()} is a no-op if the
 * collection is already there, so restarting the backend never creates
 * duplicates.
 */
@Component
public class DefaultCollectionInitializer {

    private final CollectionService collectionService;

    public DefaultCollectionInitializer(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createDefaultCollectionIfMissing() {
        collectionService.ensureDefaultCollection();
    }
}
