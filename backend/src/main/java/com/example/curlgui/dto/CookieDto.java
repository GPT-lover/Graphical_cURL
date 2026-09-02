package com.example.curlgui.dto;

/**
 * One cookie as a name/value pair. Same shape as {@link HeaderDto}, kept as its
 * own type so the API and the editor treat cookies and headers as distinct
 * things (they render in separate sections and are combined differently when the
 * request is sent).
 */
public record CookieDto(String key, String value) {
}
