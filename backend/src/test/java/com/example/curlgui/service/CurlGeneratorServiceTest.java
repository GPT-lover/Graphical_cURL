package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.CookieDto;
import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.SendRequestDto;

/**
 * Unit tests for {@link CurlGeneratorService}. Pure - no Spring, no network.
 * The generated command targets POSIX shells (Git Bash / WSL / macOS / Linux).
 */
class CurlGeneratorServiceTest {

    private final CurlGeneratorService gen = new CurlGeneratorService();

    private static SendRequestDto req(String method, String url,
                                      List<HeaderDto> headers, List<CookieDto> cookies, String body) {
        return new SendRequestDto(method, url, headers, cookies, body);
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    // ---- Spec tests ---------------------------------------------------

    @Test
    void test1_basicGet() {
        String curl = gen.generate(req("GET", "https://example.com", List.of(), List.of(), ""));
        assertEquals("curl 'https://example.com'", curl);
        assertFalse(curl.contains("-X"));
        assertFalse(curl.contains("--data-raw"));
    }

    @Test
    void test2_postJson() {
        String curl = gen.generate(req("POST", "https://example.com/api",
                List.of(new HeaderDto("Content-Type", "application/json")), List.of(),
                "{\"name\":\"William\"}"));
        assertTrue(curl.contains("-X POST"));
        assertTrue(curl.contains("-H 'Content-Type: application/json'"));
        assertTrue(curl.contains("--data-raw '{\"name\":\"William\"}'"));
    }

    @Test
    void test3_multipleHeadersAllPreservedInOrderIncludingDuplicates() {
        String curl = gen.generate(req("GET", "https://example.com",
                List.of(new HeaderDto("Accept", "application/json"),
                        new HeaderDto("Accept", "text/plain"),
                        new HeaderDto("X-Trace", "abc")),
                List.of(), ""));
        assertTrue(curl.contains("-H 'Accept: application/json'"));
        assertTrue(curl.contains("-H 'Accept: text/plain'"));
        assertTrue(curl.contains("-H 'X-Trace: abc'"));
        assertEquals(2, countOccurrences(curl, "-H 'Accept: "));
        assertTrue(curl.indexOf("application/json") < curl.indexOf("text/plain"));
    }

    @Test
    void test4_cookiesBecomeOneMinusB() {
        String curl = gen.generate(req("GET", "https://example.com", List.of(),
                List.of(new CookieDto("session", "abc123"), new CookieDto("theme", "dark")), ""));
        assertTrue(curl.contains("-b 'session=abc123; theme=dark'"));
        assertEquals(1, countOccurrences(curl, "-b "));
    }

    @Test
    void test5_cookieValueWithEqualsIsPreserved() {
        String curl = gen.generate(req("GET", "https://example.com", List.of(),
                List.of(new CookieDto("token", "abc=def=ghi")), ""));
        assertTrue(curl.contains("-b 'token=abc=def=ghi'"));
    }

    @Test
    void test6_headerValueWithSpacesStaysOneArgument() {
        String curl = gen.generate(req("GET", "https://example.com",
                List.of(new HeaderDto("X-Test", "hello world")), List.of(), ""));
        assertTrue(curl.contains("-H 'X-Test: hello world'"));
    }

    @Test
    void test7_headerValueWithQuotesIsValidlyQuoted() {
        String curl = gen.generate(req("GET", "https://example.com",
                List.of(new HeaderDto("sec-ch-ua", "\"Not=A?Brand\";v=\"99\", \"Chromium\";v=\"151\"")),
                List.of(), ""));
        assertTrue(curl.contains(
                "-H 'sec-ch-ua: \"Not=A?Brand\";v=\"99\", \"Chromium\";v=\"151\"'"));
    }

    @Test
    void test8_jsonBodyWithQuotesStaysIntact() {
        String body = "{\"message\":\"hello world\",\"value\":\"abc\"}";
        String curl = gen.generate(req("POST", "https://example.com",
                List.of(new HeaderDto("Content-Type", "application/json")), List.of(), body));
        assertTrue(curl.contains("--data-raw '" + body + "'"));
    }

    @Test
    void test9_emptyHeaderAndCookieRowsAreOmitted() {
        String curl = gen.generate(req("POST", "https://example.com",
                List.of(new HeaderDto("Content-Type", "application/json"), new HeaderDto("", "")),
                List.of(new CookieDto("session", "abc123"), new CookieDto("", "")),
                ""));
        assertEquals(1, countOccurrences(curl, "-H "));
        assertTrue(curl.contains("-b 'session=abc123'"));
        assertFalse(curl.contains("; ="));
    }

    @Test
    void test9_noBodyMeansNoDataRaw() {
        String curl = gen.generate(req("DELETE", "https://example.com/x", List.of(), List.of(), ""));
        assertFalse(curl.contains("--data-raw"));
        assertTrue(curl.contains("-X DELETE"));
    }

    @Test
    void test10_everyMethodIsRepresented() {
        assertEquals("curl 'https://e.com'",
                gen.generate(req("GET", "https://e.com", List.of(), List.of(), "")));
        for (String m : new String[]{"POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}) {
            String curl = gen.generate(req(m, "https://e.com", List.of(), List.of(), ""));
            assertTrue(curl.contains("-X " + m), m + " -> " + curl);
        }
    }

    // ---- Extra coverage -------------------------------------------

    @Test
    void getWithABodyKeepsTheGetMethodExplicit() {
        String curl = gen.generate(req("GET", "https://e.com", List.of(), List.of(), "{\"a\":1}"));
        assertTrue(curl.contains("-X GET"));
        assertTrue(curl.contains("--data-raw '{\"a\":1}'"));
    }

    @Test
    void putAndPatchWithBodyGenerateThatMethodNotPost() {
        String put = gen.generate(req("PUT", "https://e.com", List.of(), List.of(), "{\"a\":1}"));
        assertTrue(put.contains("-X PUT"));
        assertFalse(put.contains("-X POST"));
        String patch = gen.generate(req("PATCH", "https://e.com", List.of(), List.of(), "{\"a\":1}"));
        assertTrue(patch.contains("-X PATCH"));
    }

    @Test
    void urlWithSpacesAndQueryStaysOneArgument() {
        String url = "https://example.com/api/test?hello=hello world&x=1&y=a=b";
        String curl = gen.generate(req("GET", url, List.of(), List.of(), ""));
        assertEquals("curl '" + url + "'", curl);
    }

    @Test
    void hostileHeaderValueCannotBreakOutOfItsArgument() {
        String curl = gen.generate(req("GET", "https://e.com",
                List.of(new HeaderDto("X-Evil", "x'; rm -rf / #")), List.of(), ""));
        // the single quote is closed/escaped/reopened, not left bare
        assertTrue(curl.contains("-H 'X-Evil: x'\\''; rm -rf / #'"));
    }

    @Test
    void hostileCookieValueIsQuotedSafely() {
        String curl = gen.generate(req("GET", "https://e.com", List.of(),
                List.of(new CookieDto("c", "v'; evil")), ""));
        assertTrue(curl.contains("-b 'c=v'\\''; evil'"));
    }

    @Test
    void manualCookieHeaderUsedWhenSectionEmptyAndNotEmittedAsHeader() {
        String curl = gen.generate(req("GET", "https://e.com",
                List.of(new HeaderDto("Cookie", "session=abc123"), new HeaderDto("Accept", "*/*")),
                List.of(), ""));
        assertTrue(curl.contains("-b 'session=abc123'"));
        assertFalse(curl.contains("-H 'Cookie:"));
        assertTrue(curl.contains("-H 'Accept: */*'"));
    }

    @Test
    void sectionCookiesWinOverManualCookieHeader() {
        String curl = gen.generate(req("GET", "https://e.com",
                List.of(new HeaderDto("cookie", "old=1")),   // lower-case name still matched
                List.of(new CookieDto("session", "new")), ""));
        assertTrue(curl.contains("-b 'session=new'"));
        assertFalse(curl.contains("old=1"));
        assertFalse(curl.contains("-H 'cookie:"));
    }

    @Test
    void multilineFormattingUsesBackslashContinuations() {
        String curl = gen.generate(req("POST", "https://e.com",
                List.of(new HeaderDto("Accept", "*/*")), List.of(), "{}"));
        assertTrue(curl.startsWith("curl 'https://e.com' \\\n  -X POST \\\n  -H 'Accept: */*' \\\n  --data-raw '{}'"));
    }

    @Test
    void nullDtoAndBlankUrlAreRejected() {
        assertThrows(InvalidRequestException.class, () -> gen.generate(null));
        assertThrows(InvalidRequestException.class,
                () -> gen.generate(req("GET", "  ", List.of(), List.of(), "")));
    }
}
