package com.example.curlgui.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.curlgui.dto.ChainStartedDto;
import com.example.curlgui.dto.ChainStatusDto;
import com.example.curlgui.dto.RunChainRequestDto;
import com.example.curlgui.service.ChainService;

/**
 * "Run a chain of requests" endpoints. Thin - {@link ChainService} does the
 * work; {@link ApiExceptionHandler} maps 400 (bad chain/loops or an unknown
 * environment variable) and 404 (unknown chain id).
 *
 * <p>Poll model, same as run-multiple: {@code POST} starts an async chain run
 * and returns a {@code chainId}; the frontend {@code GET}s status (passing how
 * many change-events it already has via {@code ?offset=}) and can {@code POST
 * .../stop}.
 */
@RestController
@RequestMapping("/api/requests")
public class ChainController {

    private final ChainService chainService;

    public ChainController(ChainService chainService) {
        this.chainService = chainService;
    }

    @PostMapping("/run-chain")
    public ChainStartedDto start(@RequestBody(required = false) RunChainRequestDto body) {
        return chainService.start(body);
    }

    @GetMapping("/run-chain/{chainId}")
    public ChainStatusDto status(@PathVariable String chainId,
                                 @RequestParam(defaultValue = "0") int offset) {
        return chainService.status(chainId, offset);
    }

    @PostMapping("/run-chain/{chainId}/stop")
    public ResponseEntity<Void> stop(@PathVariable String chainId) {
        chainService.stop(chainId);
        return ResponseEntity.noContent().build();
    }
}
