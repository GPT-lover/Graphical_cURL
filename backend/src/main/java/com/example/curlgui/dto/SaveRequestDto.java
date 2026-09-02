package com.example.curlgui.dto;

import java.util.List;

/**
 * Body of {@code POST /api/saved-requests} and {@code PUT /api/saved-requests/{id}}.
 *
 * <p>There is deliberately <b>no cookies field</b> - cookies are never saved.
 * {@code headers} is sanitised (credential headers removed) by the service
 * before it is stored.
 */
public record SaveRequestDto(
        String name,
        Long collectionId,
        String method,
        String url,
        List<HeaderDto> headers,
        String body
) {
}
