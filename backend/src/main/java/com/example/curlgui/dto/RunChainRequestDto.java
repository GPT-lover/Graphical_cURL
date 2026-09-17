package com.example.curlgui.dto;

import java.util.List;

/**
 * Body of {@code POST /api/requests/run-chain}.
 *
 * <pre>
 * {
 *   "requests": [ { ... SendRequestDto ... }, { ... SendRequestDto ... } ],
 *   "loops": 3,
 *   "cooldownMs": 1000,
 *   "delayMode": "FIXED",
 *   "jitterMs": 50,
 *   "windowMs": 100000
 * }
 * </pre>
 *
 * Reuses {@link SendRequestDto} for each step - no duplicate request model.
 * {@code loops} repeats the <em>whole</em> chain that many times; each request in
 * the chain is dispatched in order but the chain runner never waits for a
 * request's HTTP response before dispatching the next one (see {@code
 * ChainRunner}).
 *
 * <p>{@code cooldownMs} is an optional pause <em>between</em> complete loop
 * iterations (never between the individual requests inside a chain, and never
 * after the final iteration). Boxed so the service can treat a missing/null
 * value as {@code 0} (no cooldown - the original behaviour) and give its own
 * validation message for a negative or out-of-range value.
 *
 * <p>{@code delayMode} selects how that pause is computed - {@code FIXED} (the
 * original behaviour, using {@code cooldownMs} as-is), {@code JITTER} (a fresh
 * random pause per iteration, uniform within {@code cooldownMs +/- jitterMs})
 * or {@code WINDOW} (all {@code loops} iterations dispatched at random,
 * irregularly-spaced times within the next {@code windowMs} - including the
 * first iteration, unlike FIXED/JITTER). Missing/null {@code delayMode}
 * defaults to {@code FIXED}; {@code jitterMs} is only used for {@code JITTER}
 * and {@code windowMs} only for {@code WINDOW}.
 */
public record RunChainRequestDto(
        List<SendRequestDto> requests,
        Integer loops,
        Long cooldownMs,
        String delayMode,
        Long jitterMs,
        Long windowMs
) {
}
