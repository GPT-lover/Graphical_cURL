package com.example.curlgui.service;

/**
 * Thrown when an operation clashes with existing data (e.g. a collection name is
 * already taken). The controller advice turns it into HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
