package com.example.curlgui.environments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.CookieDto;
import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.service.EnvironmentVariableResolver;
import com.example.curlgui.service.UnresolvedVariableException;

/** Pure unit tests for {@link EnvironmentVariableResolver}. No Spring, no DB. */
class EnvironmentVariableResolverTest {

    private final EnvironmentVariableResolver resolver = new EnvironmentVariableResolver();

    private static final Map<String, String> VARS = Map.of(
            "BASE_URL", "https://api.example.com",
            "USER_ID", "123",
            "TOKEN", "secret-token",
            "OPTIONAL", "");

    @Test
    void resolvesASingleVariable() {
        assertEquals("https://api.example.com/users",
                resolver.resolve("{{BASE_URL}}/users", VARS));
    }

    @Test
    void resolvesMultipleVariablesInOneString() {
        assertEquals("https://api.example.com/users/123?token=secret-token",
                resolver.resolve("{{BASE_URL}}/users/{{USER_ID}}?token={{TOKEN}}", VARS));
    }

    @Test
    void toleratesWhitespaceInsideBraces() {
        assertEquals("https://api.example.com/x", resolver.resolve("{{ BASE_URL }}/x", VARS));
    }

    @Test
    void headerValueLikeStringResolves() {
        assertEquals("Bearer secret-token", resolver.resolve("Bearer {{TOKEN}}", VARS));
    }

    @Test
    void emptyVariableSubstitutesAnEmptyString() {
        assertEquals("helloworld", resolver.resolve("hello{{OPTIONAL}}world", VARS));
        assertEquals("https://example.com/test",
                resolver.resolve("https://example.com/{{OPTIONAL}}test", VARS));
    }

    @Test
    void unknownVariableThrowsAndNamesIt() {
        UnresolvedVariableException ex = assertThrows(UnresolvedVariableException.class,
                () -> resolver.resolve("{{DOES_NOT_EXIST}}/test", VARS));
        assertTrue(ex.getMessage().contains("DOES_NOT_EXIST"));
    }

    @Test
    void errorMessageDoesNotLeakOtherVariableValues() {
        UnresolvedVariableException ex = assertThrows(UnresolvedVariableException.class,
                () -> resolver.resolve("{{BASE_URL}}/{{MISSING}}", VARS));
        assertTrue(ex.getMessage().contains("MISSING"));
        assertTrue(!ex.getMessage().contains("secret-token"));
        assertTrue(!ex.getMessage().contains("api.example.com"));
    }

    @Test
    void doesNotRecursivelyExpandAValueThatContainsAPlaceholder() {
        Map<String, String> nested = Map.of("HOST", "example.com", "BASE_URL", "https://{{HOST}}");
        // one pass only: {{BASE_URL}} -> "https://{{HOST}}", the inner {{HOST}} is left as text
        assertEquals("https://{{HOST}}/users", resolver.resolve("{{BASE_URL}}/users", nested));
    }

    @Test
    void resolveRequestBuildsAnExecutionCopyAndLeavesTheOriginalUntouched() {
        SendRequestDto original = new SendRequestDto(
                "POST",
                "{{BASE_URL}}/users/{{USER_ID}}",
                List.of(new HeaderDto("Authorization", "Bearer {{TOKEN}}"),
                        new HeaderDto("X-Env", "{{USER_ID}}")),
                List.of(new CookieDto("session", "{{TOKEN}}")),
                "{\"id\":\"{{USER_ID}}\"}",
                7L);

        SendRequestDto resolved = resolver.resolveRequest(original, VARS);

        // execution copy is fully resolved
        assertEquals("https://api.example.com/users/123", resolved.url());
        assertEquals("Bearer secret-token", resolved.headers().get(0).value());
        assertEquals("Authorization", resolved.headers().get(0).key()); // names untouched
        assertEquals("123", resolved.headers().get(1).value());
        assertEquals("secret-token", resolved.cookies().get(0).value());
        assertEquals("session", resolved.cookies().get(0).key());
        assertEquals("{\"id\":\"123\"}", resolved.body());
        assertEquals(7L, resolved.environmentId());

        // original is byte-for-byte unchanged
        assertEquals("{{BASE_URL}}/users/{{USER_ID}}", original.url());
        assertEquals("Bearer {{TOKEN}}", original.headers().get(0).value());
        assertEquals("{{TOKEN}}", original.cookies().get(0).value());
        assertEquals("{\"id\":\"{{USER_ID}}\"}", original.body());
    }

    @Test
    void resolveRequestCollectsEveryUnknownAcrossTheWholeRequest() {
        SendRequestDto original = new SendRequestDto(
                "GET", "{{MISSING_A}}/x",
                List.of(new HeaderDto("X-Y", "{{MISSING_B}}")),
                List.of(), "{{MISSING_C}}", null);

        UnresolvedVariableException ex = assertThrows(UnresolvedVariableException.class,
                () -> resolver.resolveRequest(original, VARS));
        assertTrue(ex.getMessage().contains("MISSING_A"));
        assertTrue(ex.getMessage().contains("MISSING_B"));
        assertTrue(ex.getMessage().contains("MISSING_C"));
    }

    @Test
    void aRequestWithNoPlaceholdersIsReturnedEssentiallyUnchanged() {
        SendRequestDto original = new SendRequestDto(
                "GET", "https://example.com/plain", List.of(), List.of(), "", null);
        SendRequestDto resolved = resolver.resolveRequest(original, Map.of());
        assertEquals("https://example.com/plain", resolved.url());
    }
}
