package com.example.curlgui.dto;

/**
 * Body of {@code POST /api/requests/run-multiple}.
 *
 * <pre>
 * {
 *   "request": { ... a normal SendRequestDto ... },
 *   "runs": 10,
 *   "delayMs": 100,
 *   "mode": "SEQUENTIAL"
 * }
 * </pre>
 *
 * Reuses {@link SendRequestDto} - no duplicate request model. {@code runs} /
 * {@code delayMs} are boxed so the service can give its own validation messages
 * for missing/out-of-range values; {@code mode} is a String parsed leniently
 * (defaults to SEQUENTIAL).
 */
public record RunMultipleRequestDto(
        SendRequestDto request,
        Integer runs,
        Long delayMs,
        String mode
) {
}
