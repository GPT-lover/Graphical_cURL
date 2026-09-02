package com.example.curlgui.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.RequestHistoryDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.dto.SendResponseDto;
import com.example.curlgui.model.RequestHistory;
import com.example.curlgui.repository.RequestHistoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persists and serves the request history.
 *
 * <p>{@code @Service} = Spring-managed business logic. It sits between
 * {@code RequestService} (which calls {@link #record}) and
 * {@link RequestHistoryRepository} (SQLite). All database work lives here, not in
 * the controller.
 *
 * <h3>What is stored</h3>
 * method, URL, <em>non-sensitive</em> headers (as JSON text), body, status code,
 * duration, timestamp. <b>Never</b> stored: the Cookie / Authorization / any
 * token-like header, and cookies (which have no column at all). See
 * {@link SensitiveHeaders}.
 *
 * <h3>Size cap</h3>
 * After each insert the table is trimmed to {@link #MAX_ENTRIES} newest rows.
 *
 * <h3>Failure policy</h3>
 * {@link #record} may throw; the caller ({@code RequestService}) catches it so a
 * history problem never turns a successful HTTP request into a failed one.
 * Nothing sensitive is logged.
 */
@Service
public class RequestHistoryService {

    /** Keep at most this many history rows. */
    static final int MAX_ENTRIES = 100;

    private static final Logger log = LoggerFactory.getLogger(RequestHistoryService.class);
    private static final TypeReference<List<HeaderDto>> HEADER_LIST = new TypeReference<>() {
    };

    private final RequestHistoryRepository repository;
    private final SensitiveHeaders sensitiveHeaders;
    private final ObjectMapper objectMapper;

    public RequestHistoryService(RequestHistoryRepository repository,
                                 SensitiveHeaders sensitiveHeaders,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.sensitiveHeaders = sensitiveHeaders;
        this.objectMapper = objectMapper;
    }

    /**
     * Save a history row for a request that was just sent, then trim to the cap.
     * The passed-in {@code request} is only read - it is never modified, so the
     * HTTP request that was actually sent still had every header/cookie the user
     * entered.
     */
    @Transactional
    public void record(SendRequestDto request, SendResponseDto response) {
        RequestHistory entity = new RequestHistory();
        entity.setMethod(request.method());
        entity.setUrl(request.url());
        entity.setHeaders(writeHeadersJson(sanitize(request.headers())));
        entity.setBody(request.body() == null ? "" : request.body());
        entity.setStatusCode(response.statusCode());
        entity.setDurationMs(response.durationMs());
        entity.setCreatedAt(Instant.now().toString());

        repository.save(entity);
        repository.deleteAllButNewest(MAX_ENTRIES);
    }

    /** All history, newest first, with headers parsed back into a list. */
    @Transactional(readOnly = true)
    public List<RequestHistoryDto> list() {
        List<RequestHistoryDto> out = new ArrayList<>();
        for (RequestHistory row : repository.findAllByOrderByIdDesc()) {
            out.add(new RequestHistoryDto(
                    row.getId(),
                    row.getMethod(),
                    row.getUrl(),
                    readHeadersJson(row.getHeaders()),
                    row.getBody(),
                    row.getStatusCode(),
                    row.getDurationMs(),
                    row.getCreatedAt()));
        }
        return out;
    }

    /** Delete one entry. Returns {@code false} if it did not exist. */
    @Transactional
    public boolean delete(long id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }

    /** Delete all history. */
    @Transactional
    public void clear() {
        repository.deleteAll();
    }

    // ------------------------------------------------------------------

    /** Drop blank-key rows and any header {@link SensitiveHeaders} flags. */
    private List<HeaderDto> sanitize(List<HeaderDto> headers) {
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

    private String writeHeadersJson(List<HeaderDto> headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception ex) {
            log.warn("Could not serialise history headers ({}); storing an empty list",
                    ex.getClass().getSimpleName());
            return "[]";
        }
    }

    private List<HeaderDto> readHeadersJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, HEADER_LIST);
        } catch (Exception ex) {
            log.warn("Could not parse stored history headers ({}); returning an empty list",
                    ex.getClass().getSimpleName());
            return List.of();
        }
    }
}
