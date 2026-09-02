package com.example.curlgui.dto;

import java.util.List;

/**
 * One collection plus its saved requests (summaries only), as returned by
 * {@code GET /api/collections}.
 *
 * <pre>
 * { "id": 1, "name": "Record API",
 *   "requests": [ { "id": 10, "name": "Rate Release" } ] }
 * </pre>
 */
public record CollectionDto(Long id, String name, List<SavedRequestSummaryDto> requests) {
}
