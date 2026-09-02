package com.example.curlgui.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.curlgui.dto.SaveRequestDto;
import com.example.curlgui.dto.SavedRequestDto;
import com.example.curlgui.service.SavedRequestService;

/**
 * REST endpoints for saved requests. Thin: delegates to
 * {@link SavedRequestService}; {@link ApiExceptionHandler} maps 400 / 404.
 *
 * <p>The backend sanitises headers (drops credentials) before persisting, and
 * {@code SaveRequestDto} has no cookies field, so cookies can't be saved.
 */
@RestController
@RequestMapping("/api/saved-requests")
public class SavedRequestController {

    private final SavedRequestService savedRequestService;

    public SavedRequestController(SavedRequestService savedRequestService) {
        this.savedRequestService = savedRequestService;
    }

    @PostMapping
    public ResponseEntity<SavedRequestDto> create(@RequestBody(required = false) SaveRequestDto body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRequestService.create(body));
    }

    @GetMapping("/{id}")
    public SavedRequestDto get(@PathVariable long id) {
        return savedRequestService.get(id);
    }

    @PutMapping("/{id}")
    public SavedRequestDto update(@PathVariable long id,
                                  @RequestBody(required = false) SaveRequestDto body) {
        return savedRequestService.update(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        savedRequestService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
