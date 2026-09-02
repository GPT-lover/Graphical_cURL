package com.example.curlgui.service;

/**
 * Thrown when the request the frontend gave us is not something we can attempt:
 * empty/invalid URL, non-http(s) scheme, unsupported HTTP method.
 *
 * The controller turns this into HTTP 400 (Bad Request) + an {@code ErrorResponseDto}.
 * Extends {@link RuntimeException} so we don't have to declare it everywhere.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
