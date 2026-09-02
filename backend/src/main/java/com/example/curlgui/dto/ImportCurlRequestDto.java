package com.example.curlgui.dto;

/**
 * Body of {@code POST /api/requests/import-curl}: the raw cURL command the user
 * pasted, as a single string.
 *
 * <pre>{ "curl": "curl 'https://example.com' -H 'accept: application/json'" }</pre>
 *
 * This string is untrusted input. It is only ever <em>parsed</em> - never run
 * through a shell, {@code Runtime.exec}, or {@code ProcessBuilder}.
 */
public record ImportCurlRequestDto(String curl) {
}
