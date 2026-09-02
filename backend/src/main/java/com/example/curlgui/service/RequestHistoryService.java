package com.example.curlgui.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.curlgui.dto.RequestHistoryDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.dto.SendResponseDto;
import com.example.curlgui.model.RequestHistory;
import com.example.curlgui.repository.RequestHistoryRepository;

/**
 * Persists and serves the request history.
 *
 * <p>{@code @Service} = Spring-managed business logic. It sits between
 * {@code RequestService} (which calls {@link #record}) and
 * {@link RequestHistoryRepository} (SQLite). All database work lives here, not in
 * the controller.
 *
 * <h3>What is stored</h3>
 * method, URL, <em>non-sensitive</em> headers (as JSON text, via
 * {@link HeaderSanitizer}), body, status code, duration, timestamp. <b>Never</b>
 * stored: the Cookie / Authorization / any token-like header, and cookies (which
 * have no column at all).
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

    private final RequestHistoryRepository repository;
    private final HeaderSanitizer headerSanitizer;

    public RequestHistoryService(RequestHistoryRepository repository,
                                 HeaderSanitizer headerSanitizer) {
        this.repository = repository;
        this.headerSanitizer = headerSanitizer;
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
        entity.setHeaders(headerSanitizer.sanitizedJson(request.headers()));
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
                    headerSanitizer.fromJson(row.getHeaders()),
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
}
