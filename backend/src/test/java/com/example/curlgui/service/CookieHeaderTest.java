package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.CookieDto;

/**
 * Unit tests for building the single outgoing {@code Cookie} header.
 * Pure logic - no Spring, no HttpClient.
 */
class CookieHeaderTest {

    private static String header(List<CookieDto> cookies) {
        return CookieHeader.resolve(cookies, null).value();
    }

    // ---- Tests from the phase spec ------------------------------------

    @Test
    void test1_noCookiesProducesNoHeader() {
        assertNull(header(List.of()));
        assertNull(CookieHeader.resolve(null, null).value());
    }

    @Test
    void test2_oneCookie() {
        assertEquals("session=abc123", header(List.of(new CookieDto("session", "abc123"))));
    }

    @Test
    void test3_multipleCookiesJoinWithSemicolonSpace() {
        assertEquals("session=abc123; theme=dark",
                header(List.of(new CookieDto("session", "abc123"), new CookieDto("theme", "dark"))));
    }

    @Test
    void test4_emptyRowsAreDropped() {
        assertEquals("session=abc123",
                header(List.of(new CookieDto("session", "abc123"), new CookieDto("", ""))));
    }

    @Test
    void test5_equalsInValueIsPreserved() {
        assertEquals("token=abc=def=ghi",
                header(List.of(new CookieDto("token", "abc=def=ghi"))));
    }

    // ---- Validation / edge cases -----------------------------------

    @Test
    void nullEntriesAndBlankNamesAreIgnored() {
        List<CookieDto> cookies = java.util.Arrays.asList(
                null,
                new CookieDto(null, "x"),
                new CookieDto("  ", "y"),
                new CookieDto("a", "1"));
        assertEquals("a=1", header(cookies));
    }

    @Test
    void namesAndValuesAreTrimmed() {
        assertEquals("session=abc123",
                header(List.of(new CookieDto("  session  ", "  abc123  "))));
    }

    @Test
    void emptyValueIsAllowed() {
        assertEquals("flag=", header(List.of(new CookieDto("flag", ""))));
    }

    @Test
    void duplicateNamesUseTheLastValueWithAWarning() {
        CookieHeader.Result r = CookieHeader.resolve(
                List.of(new CookieDto("session", "first"), new CookieDto("session", "second")),
                null);
        assertEquals("session=second", r.value());
        assertEquals(1, r.warnings().size());
        assertTrue(r.warnings().get(0).contains("session"));
    }

    @Test
    void cookieNamesAreCaseSensitive() {
        CookieHeader.Result r = CookieHeader.resolve(
                List.of(new CookieDto("Session", "1"), new CookieDto("session", "2")), null);
        assertEquals("Session=1; session=2", r.value());
        assertTrue(r.warnings().isEmpty());
    }

    // ---- Manual "Cookie" header interaction -----------------------

    @Test
    void manualCookieHeaderUsedWhenSectionEmpty() {
        assertEquals("x=1; y=2", CookieHeader.resolve(List.of(), "x=1; y=2").value());
        assertEquals("x=1", CookieHeader.resolve(List.of(new CookieDto("", "")), "x=1").value());
    }

    @Test
    void sectionWinsOverManualCookieHeaderWithAWarning() {
        CookieHeader.Result r = CookieHeader.resolve(
                List.of(new CookieDto("a", "1")), "x=1; y=2");
        assertEquals("a=1", r.value());
        assertEquals(1, r.warnings().size());
        assertTrue(r.warnings().get(0).toLowerCase().contains("replaced"));
    }

    @Test
    void warningsNeverContainCookieValues() {
        CookieHeader.Result r = CookieHeader.resolve(
                List.of(new CookieDto("session", "SECRET_VALUE"),
                        new CookieDto("session", "OTHER_SECRET")),
                "session=MANUAL_SECRET");
        for (String w : r.warnings()) {
            assertTrue(!w.contains("SECRET_VALUE") && !w.contains("OTHER_SECRET")
                    && !w.contains("MANUAL_SECRET"), "warning leaked a value: " + w);
        }
    }
}
