package com.example.curlgui.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.curlgui.dto.RunMultipleRequestDto;
import com.example.curlgui.dto.RunStartedDto;
import com.example.curlgui.dto.RunStatusDto;
import com.example.curlgui.service.RunMultipleService;

/**
 * "Run the current request N times" endpoints. Thin - {@link RunMultipleService}
 * does the work; {@link ApiExceptionHandler} maps 400 (bad runs/delay/mode or an
 * unknown environment variable) and 404 (unknown run id).
 *
 * <p>Poll model: {@code POST} starts an async loop and returns a {@code runId};
 * the frontend {@code GET}s status (passing how many results it already has via
 * {@code ?offset=}) and can {@code POST .../stop}.
 */
@RestController
@RequestMapping("/api/requests")
public class RunMultipleController {

    private final RunMultipleService runMultipleService;

    public RunMultipleController(RunMultipleService runMultipleService) {
        this.runMultipleService = runMultipleService;
    }

    @PostMapping("/run-multiple")
    public RunStartedDto start(@RequestBody(required = false) RunMultipleRequestDto body) {
        return runMultipleService.start(body);
    }

    @GetMapping("/run-multiple/{runId}")
    public RunStatusDto status(@PathVariable String runId,
                               @RequestParam(defaultValue = "0") int offset) {
        return runMultipleService.status(runId, offset);
    }

    @PostMapping("/run-multiple/{runId}/stop")
    public ResponseEntity<Void> stop(@PathVariable String runId) {
        runMultipleService.stop(runId);
        return ResponseEntity.noContent().build();
    }
}
