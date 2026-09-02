package com.example.curlgui.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.curlgui.dto.CollectionDto;
import com.example.curlgui.dto.SavedRequestSummaryDto;
import com.example.curlgui.model.RequestCollection;
import com.example.curlgui.model.SavedRequest;
import com.example.curlgui.repository.CollectionRepository;
import com.example.curlgui.repository.SavedRequestRepository;

/**
 * CRUD for collections (folders of saved requests).
 *
 * <p>Rules:
 * <ul>
 *   <li>names must not be blank ({@link InvalidRequestException} -> 400)</li>
 *   <li>names are unique, case-insensitively ({@link ConflictException} -> 409) -
 *       this also makes {@link #ensureDefaultCollection()} idempotent</li>
 *   <li>deleting a collection deletes its saved requests too (explicit) -
 *       no orphans</li>
 * </ul>
 */
@Service
public class CollectionService {

    /** The collection created on first run so the user can save immediately. */
    public static final String DEFAULT_COLLECTION_NAME = "My Requests";

    private final CollectionRepository collectionRepository;
    private final SavedRequestRepository savedRequestRepository;

    public CollectionService(CollectionRepository collectionRepository,
                             SavedRequestRepository savedRequestRepository) {
        this.collectionRepository = collectionRepository;
        this.savedRequestRepository = savedRequestRepository;
    }

    /** All collections with their saved-request summaries. Two queries, no N+1. */
    @Transactional(readOnly = true)
    public List<CollectionDto> listAll() {
        Map<Long, List<SavedRequestSummaryDto>> byCollection = new LinkedHashMap<>();
        for (SavedRequest sr : savedRequestRepository.findAllByOrderByIdAsc()) {
            byCollection
                    .computeIfAbsent(sr.getCollectionId(), k -> new ArrayList<>())
                    .add(new SavedRequestSummaryDto(sr.getId(), sr.getName()));
        }
        List<CollectionDto> out = new ArrayList<>();
        for (RequestCollection c : collectionRepository.findAllByOrderByIdAsc()) {
            out.add(new CollectionDto(c.getId(), c.getName(),
                    byCollection.getOrDefault(c.getId(), List.of())));
        }
        return out;
    }

    @Transactional
    public CollectionDto create(String rawName) {
        String name = requireName(rawName);
        collectionRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw new ConflictException("A collection named \"" + name + "\" already exists.");
        });
        String now = Instant.now().toString();
        RequestCollection c = new RequestCollection();
        c.setName(name);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        c = collectionRepository.save(c);
        return new CollectionDto(c.getId(), c.getName(), List.of());
    }

    @Transactional
    public CollectionDto rename(long id, String rawName) {
        String name = requireName(rawName);
        RequestCollection c = collectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Collection " + id + " not found."));
        collectionRepository.findByNameIgnoreCase(name).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ConflictException("A collection named \"" + name + "\" already exists.");
            }
        });
        c.setName(name);
        c.setUpdatedAt(Instant.now().toString());
        collectionRepository.save(c);
        return new CollectionDto(c.getId(), c.getName(), List.of());
    }

    /** Delete a collection and every saved request inside it. */
    @Transactional
    public void delete(long id) {
        if (!collectionRepository.existsById(id)) {
            throw new NotFoundException("Collection " + id + " not found.");
        }
        savedRequestRepository.deleteByCollectionId(id);
        collectionRepository.deleteById(id);
    }

    /** Create the default collection if it isn't there yet. Called once on startup. */
    @Transactional
    public void ensureDefaultCollection() {
        if (collectionRepository.findByNameIgnoreCase(DEFAULT_COLLECTION_NAME).isEmpty()) {
            create(DEFAULT_COLLECTION_NAME);
        }
    }

    private String requireName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new InvalidRequestException("Collection name must not be blank.");
        }
        return name;
    }
}
