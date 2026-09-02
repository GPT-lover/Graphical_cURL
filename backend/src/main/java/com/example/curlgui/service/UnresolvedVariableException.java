package com.example.curlgui.service;

import java.util.Collection;

/**
 * Thrown when a request still contains {@code {{PLACEHOLDER}}} references that
 * the active environment does not define. Extends {@link InvalidRequestException}
 * so the existing controller mapping turns it into HTTP 400.
 *
 * <p>The message lists only the unknown <em>names</em> - never the values of any
 * other variable.
 */
public class UnresolvedVariableException extends InvalidRequestException {

    public UnresolvedVariableException(Collection<String> names) {
        super("Unknown environment variable" + (names.size() == 1 ? "" : "s")
                + ": " + String.join(", ", names));
    }
}
