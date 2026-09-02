package com.example.curlgui.dto;

/**
 * Body of the variable create/update endpoints. An empty {@code value} is
 * allowed on purpose; a blank {@code key} is rejected.
 */
public record SaveVariableDto(String key, String value) {
}
