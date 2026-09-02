package com.example.curlgui.dto;

/**
 * One HTTP header as sent by the frontend: a key/value pair.
 *
 * The frontend models headers as a list of pairs (not a map) because the editor
 * lets you have blank / half-typed rows while editing. The backend skips rows
 * with an empty key.
 */
public record HeaderDto(String key, String value) {
}
