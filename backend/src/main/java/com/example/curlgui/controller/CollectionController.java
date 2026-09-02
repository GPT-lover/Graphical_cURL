package com.example.curlgui.controller;

import java.util.List;

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

import com.example.curlgui.dto.CollectionDto;
import com.example.curlgui.dto.CreateCollectionDto;
import com.example.curlgui.service.CollectionService;

/**
 * REST endpoints for collections. Thin: it delegates to {@link CollectionService}
 * and lets {@link ApiExceptionHandler} translate 400 / 404 / 409.
 */
@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /** All collections, each with its saved-request summaries. */
    @GetMapping
    public List<CollectionDto> list() {
        return collectionService.listAll();
    }

    @PostMapping
    public ResponseEntity<CollectionDto> create(@RequestBody(required = false) CreateCollectionDto body) {
        CollectionDto created = collectionService.create(body == null ? null : body.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public CollectionDto rename(@PathVariable long id,
                               @RequestBody(required = false) CreateCollectionDto body) {
        return collectionService.rename(id, body == null ? null : body.name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        collectionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
