package com.example.curlgui.dto;

import java.util.List;

/**
 * A full saved request, as returned by {@code GET /api/saved-requests/{id}} and
 * the create/update endpoints.
 *
 * <p>No cookies, no credential headers - they were never stored.
 */
public record SavedRequestDto(
        Long id,
        String name,
        Long collectionId,
        String method,
        String url,
        List<HeaderDto> headers,
        String body,
        String createdAt,
        String updatedAt
) {
}
