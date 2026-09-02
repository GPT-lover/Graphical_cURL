package com.example.curlgui.service;

/**
 * Thrown by {@link CurlParserService} / {@link CurlTokenizer} when a pasted cURL
 * command cannot be turned into a request the editor can represent.
 *
 * The message is always written to be safe and useful to show the user directly
 * (e.g. "Could not determine the request URL from the cURL command."). The
 * controller turns this into HTTP 400 + an {@code ErrorResponseDto}. It never
 * carries a stack trace or the original input to the client.
 */
public class CurlParseException extends RuntimeException {

    public CurlParseException(String message) {
        super(message);
    }
}
