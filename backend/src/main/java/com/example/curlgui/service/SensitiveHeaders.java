package com.example.curlgui.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Decides whether an HTTP header name identifies a credential and must not be
 * persisted to the request history.
 *
 * <p>Reusable, case-insensitive, and deliberately conservative: a header is
 * treated as sensitive only if its name is a well-known credential header or
 * clearly contains a credential term. A harmless header is not dropped just
 * because it happens to contain the word "key" (that is why the fragment list
 * uses {@code api-key} / {@code apikey}, not a bare {@code key}).
 *
 * <p>{@code @Component} so it can be injected; it has no state, so tests just
 * {@code new SensitiveHeaders()}.
 */
@Component
public class SensitiveHeaders {

    /** Exact header names (lower-cased) that always carry credentials. */
    private static final Set<String> EXACT = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "www-authenticate",
            "x-api-key",
            "x-auth-token",
            "x-access-token",
            "x-csrf-token",
            "x-xsrf-token",
            "x-amz-security-token"
    );

    /** Name fragments that clearly indicate a credential. */
    private static final List<String> FRAGMENTS = List.of(
            "token",
            "secret",
            "password",
            "passwd",
            "apikey",
            "api-key",
            "api_key",
            "auth-token",
            "access-key"
    );

    public boolean isSensitive(String headerName) {
        if (headerName == null) {
            return false;
        }
        String name = headerName.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return false;
        }
        if (EXACT.contains(name)) {
            return true;
        }
        for (String fragment : FRAGMENTS) {
            if (name.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
