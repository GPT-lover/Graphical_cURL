package com.example.curlgui.controller;

import com.example.curlgui.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Health/handshake endpoint.
 *
 * <p>{@code @RestController} = {@code @Controller} + {@code @ResponseBody}: every
 * method's return value is serialised straight to the HTTP response body as JSON,
 * instead of being treated as the name of an HTML view.
 *
 * <p>{@code @RequestMapping("/api")} sets a path prefix shared by every method in
 * this class, so {@code health()} below is reachable at {@code GET /api/health}.
 *
 * <p>This endpoint exists purely so the frontend can confirm it can reach the
 * backend. The real request-sending endpoints arrive in later phases.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                "curl-gui-backend",
                Instant.now().toString()
        );
    }
}
