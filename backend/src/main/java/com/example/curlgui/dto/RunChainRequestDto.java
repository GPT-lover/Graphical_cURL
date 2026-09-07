package com.example.curlgui.dto;

import java.util.List;

/**
 * Body of {@code POST /api/requests/run-chain}.
 *
 * <pre>
 * {
 *   "requests": [ { ... SendRequestDto ... }, { ... SendRequestDto ... } ],
 *   "loops": 3,
 *   "cooldownMs": 1000
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
 */
public record RunChainRequestDto(
        List<SendRequestDto> requests,
        Integer loops,
        Long cooldownMs
) {
}
