package com.example.curlgui.service;

/**
 * Thrown when we tried to perform the request but the network exchange failed:
 * unknown host, connection refused, connect/read timeout, other I/O error.
 *
 * The controller turns this into HTTP 502 (Bad Gateway) - i.e. "this API is
 * fine, but the upstream server it tried to reach is not" - plus an
 * {@code ErrorResponseDto}.
 *
 * {@code detail} is a short extra line (typically the cause's message). It is
 * never a stack trace.
 */
public class RequestExecutionException extends RuntimeException {

    private final String detail;

    public RequestExecutionException(String message, String detail) {
        super(message);
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }
}
