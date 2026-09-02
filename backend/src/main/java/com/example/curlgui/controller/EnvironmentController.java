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

import com.example.curlgui.dto.CreateEnvironmentDto;
import com.example.curlgui.dto.EnvironmentDto;
import com.example.curlgui.dto.EnvironmentSummaryDto;
import com.example.curlgui.dto.EnvironmentVariableDto;
import com.example.curlgui.dto.SaveVariableDto;
import com.example.curlgui.service.EnvironmentService;
import com.example.curlgui.service.EnvironmentVariableService;

/**
 * REST endpoints for environments and their variables. Thin - delegates to the
 * services; {@link ApiExceptionHandler} maps 400 / 404 / 409.
 *
 * <p>Variable <em>values</em> are returned only by {@code GET /{id}} and the
 * variable create/update responses (the management UI). The list endpoint
 * ({@code GET /api/environments}) never includes values.
 */
@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final EnvironmentVariableService variableService;

    public EnvironmentController(EnvironmentService environmentService,
                                EnvironmentVariableService variableService) {
        this.environmentService = environmentService;
        this.variableService = variableService;
    }

    // ---- environments ------------------------------------------------

    @GetMapping
    public List<EnvironmentSummaryDto> list() {
        return environmentService.listAll();
    }

    @GetMapping("/{id}")
    public EnvironmentDto get(@PathVariable long id) {
        return environmentService.get(id);
    }

    @PostMapping
    public ResponseEntity<EnvironmentSummaryDto> create(
            @RequestBody(required = false) CreateEnvironmentDto body) {
        EnvironmentSummaryDto created = environmentService.create(body == null ? null : body.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public EnvironmentSummaryDto rename(@PathVariable long id,
                                       @RequestBody(required = false) CreateEnvironmentDto body) {
        return environmentService.rename(id, body == null ? null : body.name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        environmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- variables -------------------------------------------------

    @PostMapping("/{environmentId}/variables")
    public ResponseEntity<EnvironmentVariableDto> createVariable(
            @PathVariable long environmentId,
            @RequestBody(required = false) SaveVariableDto body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(variableService.create(environmentId, body));
    }

    @PutMapping("/{environmentId}/variables/{variableId}")
    public EnvironmentVariableDto updateVariable(@PathVariable long environmentId,
                                                @PathVariable long variableId,
                                                @RequestBody(required = false) SaveVariableDto body) {
        return variableService.update(environmentId, variableId, body);
    }

    @DeleteMapping("/{environmentId}/variables/{variableId}")
    public ResponseEntity<Void> deleteVariable(@PathVariable long environmentId,
                                               @PathVariable long variableId) {
        variableService.delete(environmentId, variableId);
        return ResponseEntity.noContent().build();
    }
}
