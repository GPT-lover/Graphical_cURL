package com.example.curlgui.dto;

/**
 * Response of {@code POST /api/requests/export-curl}: the generated command.
 *
 * <pre>{ "curl": "curl 'https://example.com/api/rate' ..." }</pre>
 *
 * The input for that endpoint is the existing {@code SendRequestDto} - no
 * separate request DTO is needed.
 */
public record ExportCurlResponseDto(String curl) {
}
