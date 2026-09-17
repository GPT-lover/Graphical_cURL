package com.example.curlgui.dto;

/**
 * Body of {@code POST /api/requests/run-multiple}.
 *
 * <pre>
 * {
 *   "request": { ... a normal SendRequestDto ... },
 *   "runs": 10,
 *   "delayMs": 100,
 *   "mode": "SEQUENTIAL",
 *   "delayMode": "FIXED",
 *   "jitterMs": 50,
 *   "windowMs": 100000
 * }
 * </pre>
 *
 * Reuses {@link SendRequestDto} - no duplicate request model. {@code runs} /
 * {@code delayMs} are boxed so the service can give its own validation messages
 * for missing/out-of-range values; {@code mode} is a String parsed leniently
 * (defaults to SEQUENTIAL).
 *
 * <p>{@code delayMode} selects how the pause between iterations is computed -
 * {@code FIXED} (the original behaviour, using {@code delayMs} as-is),
 * {@code JITTER} (a fresh random delay per iteration, uniform within {@code
 * delayMs +/- jitterMs}) or {@code WINDOW} (all {@code runs} iterations
 * dispatched at random, irregularly-spaced times within the next {@code
 * windowMs}). Missing/null {@code delayMode} defaults to {@code FIXED}, so
 * existing clients keep working unchanged; {@code jitterMs} is only used for
 * {@code JITTER} and {@code windowMs} only for {@code WINDOW}.
 */
public record RunMultipleRequestDto(
        SendRequestDto request,
        Integer runs,
        Long delayMs,
        String mode,
        String delayMode,
        Long jitterMs,
        Long windowMs
) {
}
