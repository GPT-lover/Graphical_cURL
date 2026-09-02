package com.example.curlgui.dto;

/**
 * A DTO (Data Transfer Object): a plain object that defines the exact shape of
 * data crossing the API boundary - here, the JSON returned by
 * {@code GET /api/health}.
 *
 * <p>Keeping DTOs separate from database {@code @Entity} classes means the public
 * API contract and the internal storage model can evolve independently.
 *
 * <p>A Java {@code record} is a compact, immutable data carrier: the compiler
 * generates the constructor, field accessors, {@code equals}/{@code hashCode}
 * and {@code toString}. Jackson serialises it to
 * {@code {"status":"...","service":"...","timestamp":"..."}}.
 */
public record HealthResponse(String status, String service, String timestamp) {
}
