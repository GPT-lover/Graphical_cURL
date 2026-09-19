package com.example.curlgui.dto;

import java.util.List;

/**
 * Body of {@code POST /api/saved-requests} and {@code PUT /api/saved-requests/{id}}.
 *
 * <p>There is deliberately <b>no cookies field</b> - cookies are never saved.
 * {@code headers} is sanitised (credential headers removed) by the service
 * before it is stored.
 *
 * <p>{@code bodyType}/{@code multipart} mirror {@link SendRequestDto}: {@code
 * "raw"}/{@code null} (default) uses {@code body} as-is, {@code "multipart"}
 * saves the field list, {@code "binary"} saves the local file path in {@code
 * body}. A saved multipart/binary request stores the local file path(s) only -
 * never the file's bytes.
 */
public record SaveRequestDto(
        String name,
        Long collectionId,
        String method,
        String url,
        List<HeaderDto> headers,
        String body,
        String bodyType,
        List<MultipartFieldDto> multipart
) {
    /** Compact form without {@code bodyType}/{@code multipart} (defaults to raw). */
    public SaveRequestDto(String name, Long collectionId, String method, String url,
                          List<HeaderDto> headers, String body) {
        this(name, collectionId, method, url, headers, body, null, null);
    }
}
