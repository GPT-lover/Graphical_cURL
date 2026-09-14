package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.CookieDto;
import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.SendRequestDto;

/**
 * Unit tests for {@code {{random(N)}}} substitution. No Spring, no network -
 * the resolver is a plain component.
 */
class DynamicVariableResolverTest {

    private final DynamicVariableResolver resolver = new DynamicVariableResolver();

    private static boolean isAlphanumeric(String value) {
        return value.chars().allMatch(c -> DynamicVariableResolver.ALPHABET.indexOf(c) >= 0);
    }

    // ---- generation ------------------------------------------------

    @Test
    void producesExactlyTheRequestedNumberOfCharacters() {
        assertEquals(50, resolver.resolve("{{random(50)}}").length());
        assertEquals(10, resolver.resolve("{{random(10)}}").length());
        assertEquals(100, resolver.resolve("{{random(100)}}").length());
        assertEquals(1, resolver.resolve("{{random(1)}}").length());
    }

    @Test
    void usesOnlyTheAlphanumericCharacterSet() {
        String value = resolver.resolve("{{random(500)}}");
        assertTrue(isAlphanumeric(value), "generated value contained a non-alphanumeric character");
    }

    @Test
    void twoCallsProduceIndependentValues() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(resolver.resolve("{{random(50)}}"));
        }
        assertEquals(50, seen.size(), "the same value was generated twice");
    }

    @Test
    void eachOccurrenceInOneStringIsGeneratedIndependently() {
        String out = resolver.resolve("{{random(20)}}-{{random(20)}}");
        String[] parts = out.split("-");
        assertEquals(2, parts.length);
        assertEquals(20, parts[0].length());
        assertEquals(20, parts[1].length());
        assertNotEquals(parts[0], parts[1]);
    }

    @Test
    void templatesEmbeddedInLargerStringsAreSubstitutedInPlace() {
        String prefix = "--data-raw '[\"";
        String suffix = "\"]'";
        String out = resolver.resolve(prefix + "{{random(50)}}" + suffix);
        assertTrue(out.startsWith(prefix), out);
        assertTrue(out.endsWith(suffix), out);
        String value = out.substring(prefix.length(), out.length() - suffix.length());
        assertEquals(50, value.length());
        assertTrue(isAlphanumeric(value));
    }

    @Test
    void toleratesWhitespaceInsideTheTemplate() {
        assertEquals(8, resolver.resolve("{{ random( 8 ) }}").length());
    }

    // ---- requests --------------------------------------------------

    @Test
    void resolvesUrlHeadersCookiesAndBodyWithoutMutatingTheOriginal() {
        SendRequestDto original = new SendRequestDto(
                "POST",
                "https://example.com/items?id={{random(6)}}",
                List.of(new HeaderDto("X-Token", "t-{{random(12)}}")),
                List.of(new CookieDto("session", "{{random(9)}}")),
                "[\"{{random(50)}}\"]",
                null);

        SendRequestDto resolved = resolver.resolveRequest(original);

        assertEquals("https://example.com/items?id=".length() + 6, resolved.url().length());
        assertEquals(14, resolved.headers().get(0).value().length());
        assertEquals(9, resolved.cookies().get(0).value().length());
        assertEquals(54, resolved.body().length());

        // The stored request still carries its templates and is reusable.
        assertEquals("https://example.com/items?id={{random(6)}}", original.url());
        assertEquals("t-{{random(12)}}", original.headers().get(0).value());
        assertEquals("{{random(9)}}", original.cookies().get(0).value());
        assertEquals("[\"{{random(50)}}\"]", original.body());
    }

    @Test
    void leavesNamesMethodEnvironmentAndOptionsAlone() {
        SendRequestDto original = new SendRequestDto(
                "GET", "https://example.com",
                List.of(new HeaderDto("X-{{random(4)}}", "v")),
                List.of(new CookieDto("c-{{random(4)}}", "v")),
                "", 7L);

        SendRequestDto resolved = resolver.resolveRequest(original);

        assertEquals("GET", resolved.method());
        assertEquals(7L, resolved.environmentId());
        assertEquals("X-{{random(4)}}", resolved.headers().get(0).key());
        assertEquals("c-{{random(4)}}", resolved.cookies().get(0).key());
    }

    @Test
    void requestsWithoutTemplatesAreUnchanged() {
        SendRequestDto original = new SendRequestDto(
                "POST", "https://example.com/a?b=c",
                List.of(new HeaderDto("Content-Type", "application/json")),
                List.of(new CookieDto("session", "abc")),
                "{\"name\":\"Alex\"}", null);

        SendRequestDto resolved = resolver.resolveRequest(original);

        assertEquals(original.url(), resolved.url());
        assertEquals(original.body(), resolved.body());
        assertEquals(original.headers(), resolved.headers());
        assertEquals(original.cookies(), resolved.cookies());
    }

    @Test
    void nullAndTemplateFreeStringsComeBackAsIs() {
        assertNull(resolver.resolve(null));
        String plain = "no templates here {{NOT_RANDOM}}";
        assertSame(plain, resolver.resolve(plain));
    }

    @Test
    void nullHeaderAndCookieListsSurviveResolution() {
        SendRequestDto original =
                new SendRequestDto("GET", "https://example.com", null, null, null, null);
        SendRequestDto resolved = resolver.resolveRequest(original);
        assertNull(resolved.headers());
        assertNull(resolved.cookies());
        assertNull(resolved.body());
    }

    // ---- malformed templates ---------------------------------------

    @Test
    void randomWithoutParenthesesIsLeftForTheEnvironmentResolver() {
        assertEquals("{{random}}", resolver.resolve("{{random}}"));
    }

    @Test
    void aMissingOrNonNumericLengthIsRejected() {
        for (String bad : List.of("{{random()}}", "{{random(abc)}}", "{{random( )}}",
                "{{random(5.5)}}", "{{random(-5)}}")) {
            InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                    () -> resolver.resolve(bad), bad + " should be rejected");
            assertTrue(ex.getMessage().contains("random"), ex.getMessage());
        }
    }

    @Test
    void anOutOfRangeLengthIsRejected() {
        for (String bad : List.of("{{random(0)}}", "{{random(999999999999)}}",
                "{{random(" + (DynamicVariableResolver.MAX_LENGTH + 1) + ")}}")) {
            InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                    () -> resolver.resolve(bad), bad + " should be rejected");
            assertTrue(ex.getMessage().contains("out of range"), ex.getMessage());
        }
        assertEquals(DynamicVariableResolver.MAX_LENGTH,
                resolver.resolve("{{random(" + DynamicVariableResolver.MAX_LENGTH + ")}}").length());
    }

    @Test
    void aMalformedTemplateInTheBodyRejectsTheWholeRequest() {
        SendRequestDto original = new SendRequestDto(
                "POST", "https://example.com", List.of(), List.of(), "{{random(abc)}}", null);
        assertThrows(InvalidRequestException.class, () -> resolver.resolveRequest(original));
    }
}
