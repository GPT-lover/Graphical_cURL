package com.example.curlgui.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.curlgui.dto.RequestHistoryDto;
import com.example.curlgui.service.RequestHistoryService;

/**
 * Read/delete endpoints for the request history.
 *
 * <p>History is <em>written</em> automatically by {@code RequestService} after a
 * request is sent - the frontend never posts to save it. This controller only
 * serves and prunes it. All DB work is in {@link RequestHistoryService}.
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final RequestHistoryService historyService;

    public HistoryController(RequestHistoryService historyService) {
        this.historyService = historyService;
    }

    /** Newest first. */
    @GetMapping
    public List<RequestHistoryDto> list() {
        return historyService.list();
    }

    /** Delete one entry. 204 if removed, 404 if it was not there. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        return historyService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** Delete all history. */
    @DeleteMapping
    public ResponseEntity<Void> clear() {
        historyService.clear();
        return ResponseEntity.noContent().build();
    }
}
