package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for credential-header detection. No Spring. */
class SensitiveHeadersTest {

    private final SensitiveHeaders sensitive = new SensitiveHeaders();

    @Test
    void wellKnownCredentialHeadersAreSensitive_caseInsensitive() {
        for (String name : new String[]{
                "Authorization", "authorization", "AUTHORIZATION",
                "Proxy-Authorization", "Cookie", "cookie", "Set-Cookie",
                "X-API-Key", "x-api-key", "X-Auth-Token", "WWW-Authenticate"}) {
            assertTrue(sensitive.isSensitive(name), name + " should be sensitive");
        }
    }

    @Test
    void namesContainingCredentialTermsAreSensitive() {
        assertTrue(sensitive.isSensitive("X-Session-Token"));
        assertTrue(sensitive.isSensitive("My-Secret-Header"));
        assertTrue(sensitive.isSensitive("X-App-Password"));
        assertTrue(sensitive.isSensitive("X-Company-ApiKey"));
        assertTrue(sensitive.isSensitive("x-refresh-token"));
    }

    @Test
    void ordinaryHeadersAreNotSensitive() {
        for (String name : new String[]{
                "Content-Type", "Accept", "Accept-Language", "User-Agent",
                "Referer", "Origin", "Cache-Control", "X-Request-Id",
                "X-Trace-Id", "Content-Length", "If-None-Match"}) {
            assertFalse(sensitive.isSensitive(name), name + " should NOT be sensitive");
        }
    }

    @Test
    void isConservativeAboutTheWordKey() {
        // "key" alone is not enough - only clear credential forms.
        assertFalse(sensitive.isSensitive("X-Idempotency-Key"));
        assertFalse(sensitive.isSensitive("Sec-WebSocket-Key"));
        assertFalse(sensitive.isSensitive("X-Partition-Key"));
        assertTrue(sensitive.isSensitive("X-API-Key"));
        assertTrue(sensitive.isSensitive("X-Access-Key"));
    }

    @Test
    void nullAndBlankAreNotSensitive() {
        assertFalse(sensitive.isSensitive(null));
        assertFalse(sensitive.isSensitive("   "));
    }
}
