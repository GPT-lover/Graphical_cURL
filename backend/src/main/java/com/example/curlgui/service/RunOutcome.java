package com.example.curlgui.service;

/**
 * The result of executing one request in a loop, before it's turned into a
 * {@code RunResultDto} (which also carries the run number and classification).
 *
 * @param status     HTTP status, or {@code null} if no response was received
 * @param durationMs request duration, or {@code null}
 * @param error      "Network Error" / "Error", or {@code null} on a normal response
 */
record RunOutcome(Integer status, Long durationMs, String error) {
}
