package com.example.curlgui.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.curlgui.dto.SaveRequestDto;
import com.example.curlgui.dto.SavedRequestDto;
import com.example.curlgui.model.SavedRequest;
import com.example.curlgui.repository.CollectionRepository;
import com.example.curlgui.repository.SavedRequestRepository;

/**
 * CRUD for saved requests.
 *
 * <p>Rules:
 * <ul>
 *   <li>a name is required ({@link InvalidRequestException} -> 400)</li>
 *   <li>the target collection must exist ({@link NotFoundException} -> 404)</li>
 *   <li>headers are sanitised by {@link HeaderSanitizer} before storage; cookies
 *       and credential headers are never persisted</li>
 * </ul>
 * The {@code SaveRequestDto} passed in is only read - the request being edited /
 * sent is never modified.
 */
@Service
public class SavedRequestService {

    private final SavedRequestRepository savedRequestRepository;
    private final CollectionRepository collectionRepository;
    private final HeaderSanitizer headerSanitizer;

    public SavedRequestService(SavedRequestRepository savedRequestRepository,
                               CollectionRepository collectionRepository,
                               HeaderSanitizer headerSanitizer) {
        this.savedRequestRepository = savedRequestRepository;
        this.collectionRepository = collectionRepository;
        this.headerSanitizer = headerSanitizer;
    }

    @Transactional
    public SavedRequestDto create(SaveRequestDto dto) {
        String name = requireName(dto);
        Long collectionId = requireExistingCollection(dto.collectionId());

        String now = Instant.now().toString();
        SavedRequest entity = new SavedRequest();
        entity.setName(name);
        entity.setCollectionId(collectionId);
        applyRequestFields(entity, dto);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        return toDto(savedRequestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public SavedRequestDto get(long id) {
        return toDto(find(id));
    }

    @Transactional
    public SavedRequestDto update(long id, SaveRequestDto dto) {
        SavedRequest entity = find(id);
        entity.setName(requireName(dto));
        // A collection may be supplied to move the request; if absent, keep it.
        if (dto.collectionId() != null) {
            entity.setCollectionId(requireExistingCollection(dto.collectionId()));
        }
        applyRequestFields(entity, dto);
        entity.setUpdatedAt(Instant.now().toString());
        return toDto(savedRequestRepository.save(entity));
    }

    @Transactional
    public void delete(long id) {
        savedRequestRepository.delete(find(id));
    }

    // ------------------------------------------------------------------

    private SavedRequest find(long id) {
        return savedRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Saved request " + id + " not found."));
    }

    private String requireName(SaveRequestDto dto) {
        if (dto == null) {
            throw new InvalidRequestException("Saved-request data is missing.");
        }
        String name = dto.name() == null ? "" : dto.name().trim();
        if (name.isEmpty()) {
            throw new InvalidRequestException("Request name must not be blank.");
        }
        return name;
    }

    private Long requireExistingCollection(Long collectionId) {
        if (collectionId == null) {
            throw new InvalidRequestException("A collection must be chosen for the saved request.");
        }
        if (!collectionRepository.existsById(collectionId)) {
            throw new NotFoundException("Collection " + collectionId + " not found.");
        }
        return collectionId;
    }

    private void applyRequestFields(SavedRequest entity, SaveRequestDto dto) {
        entity.setMethod(dto.method() == null ? "GET" : dto.method());
        entity.setUrl(dto.url() == null ? "" : dto.url());
        entity.setHeaders(headerSanitizer.sanitizedJson(dto.headers())); // credentials removed here
        entity.setBody(dto.body() == null ? "" : dto.body());
    }

    private SavedRequestDto toDto(SavedRequest e) {
        return new SavedRequestDto(
                e.getId(), e.getName(), e.getCollectionId(), e.getMethod(), e.getUrl(),
                headerSanitizer.fromJson(e.getHeaders()), e.getBody(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
