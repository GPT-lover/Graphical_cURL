package com.example.curlgui.dto;

import java.util.List;

/**
 * Body of {@code POST /api/requests/run-chain}.
 *
 * <pre>
 * {
 *   "requests": [ { ... SendRequestDto ... }, { ... SendRequestDto ... } ],
 *   "loops": 3
 * }
 * </pre>
 *
 * Reuses {@link SendRequestDto} for each step - no duplicate request model.
 * {@code loops} repeats the <em>whole</em> chain that many times; each request in
 * the chain is dispatched in order but the chain runner never waits for a
 * request's HTTP response before dispatching the next one (see {@code
 * ChainRunner}).
 */
public record RunChainRequestDto(
        List<SendRequestDto> requests,
        Integer loops
) {
}
