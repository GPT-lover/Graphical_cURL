package com.example.curlgui.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One field of a {@code multipart/form-data} request body (Body Type
 * "Multipart Form" in the editor).
 *
 * <pre>
 * { "type": "text", "name": "albumId", "value": "123", "contentType": null }
 * { "type": "file", "name": "image",   "value": "C:\\Users\\Alex\\photo.jpg", "contentType": "image/jpeg" }
 * </pre>
 *
 * <ul>
 *   <li>{@code type}        - {@code "text"} or {@code "file"} (case-insensitive)</li>
 *   <li>{@code name}        - the form field name</li>
 *   <li>{@code value}       - for {@code text}: the literal value; for
 *       {@code file}: the absolute path to the local file curl should read and
 *       stream. The file's bytes are never copied into this DTO or persisted -
 *       only the path is carried around.</li>
 *   <li>{@code contentType} - {@code file} fields only: an explicit MIME type
 *       (e.g. from an imported {@code -F 'image=@photo.jpg;type=image/jpeg'}),
 *       or {@code null} to let curl detect it from the file extension.</li>
 * </ul>
 */
public record MultipartFieldDto(
        String type,
        String name,
        String value,
        String contentType
) {
    // @JsonIgnore: without it, Jackson's default bean-getter detection reads
    // this as an extra "file" JSON property (isFile() -> "file") on top of the
    // record's own components, which then fails on deserialisation with
    // UnrecognizedPropertyException wherever FAIL_ON_UNKNOWN_PROPERTIES is on
    // (e.g. SavedRequestService's stored multipart-fields JSON).
    @JsonIgnore
    public boolean isFile() {
        return "file".equalsIgnoreCase(type);
    }
}
