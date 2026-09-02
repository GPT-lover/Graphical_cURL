package com.example.curlgui.service;

/**
 * Thrown when a requested collection or saved request does not exist. The
 * controller advice turns it into HTTP 404. The message is safe to show the user.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
