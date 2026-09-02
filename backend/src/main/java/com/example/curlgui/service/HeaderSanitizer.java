package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.curlgui.dto.HeaderDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The one place that turns request headers into something safe to persist.
 *
 * <p>Used by both {@code RequestHistoryService} (Phase 7) and
 * {@code SavedRequestService} (Phase 8) so there is a single implementation of
 * "drop credentials, store as JSON". It delegates the "is this a credential
 * header?" question to {@link SensitiveHeaders}.
 *
 * <p>Nothing here logs a header value.
 */
@Component
public class HeaderSanitizer {

    private static final Logger log = LoggerFactory.getLogger(HeaderSanitizer.class);
    private static final TypeReference<List<HeaderDto>> HEADER_LIST = new TypeReference<>() {
    };

    private final SensitiveHeaders sensitiveHeaders;
    private final ObjectMapper objectMapper;

    public HeaderSanitizer(SensitiveHeaders sensitiveHeaders, ObjectMapper objectMapper) {
        this.sensitiveHeaders = sensitiveHeaders;
        this.objectMapper = objectMapper;
    }

    /**
     * Return a new list with blank-key rows and any credential header removed;
     * names are trimmed. The input list is never modified, so the request that
     * was actually sent still has every header the user entered.
     */
    public List<HeaderDto> stripSensitive(List<HeaderDto> headers) {
        List<HeaderDto> kept = new ArrayList<>();
        if (headers == null) {
            return kept;
        }
        for (HeaderDto header : headers) {
            if (header == null) {
                continue;
            }
            String name = header.key() == null ? "" : header.key().trim();
            if (name.isEmpty() || sensitiveHeaders.isSensitive(name)) {
                continue;
            }
            kept.add(new HeaderDto(name, header.value() == null ? "" : header.value()));
        }
        return kept;
    }

    /** Serialise a header list to a JSON array string. Falls back to "[]". */
    public String toJson(List<HeaderDto> headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception ex) {
            log.warn("Could not serialise headers to JSON ({}); storing an empty list",
                    ex.getClass().getSimpleName());
            return "[]";
        }
    }

    /** Parse a stored JSON array string back into a header list. Empty on failure. */
    public List<HeaderDto> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, HEADER_LIST);
        } catch (Exception ex) {
            log.warn("Could not parse stored headers JSON ({}); returning an empty list",
                    ex.getClass().getSimpleName());
            return List.of();
        }
    }

    /** Convenience: strip sensitive headers and serialise in one call. */
    public String sanitizedJson(List<HeaderDto> headers) {
        return toJson(stripSensitive(headers));
    }
}
