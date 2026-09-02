package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.CookieDto;
import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.ParsedRequestDto;

/**
 * Unit tests for the cURL parser. Plain JUnit - {@code new CurlParserService()},
 * no Spring context, no network.
 */
class CurlParserServiceTest {

    private final CurlParserService parser = new CurlParserService();

    // ---- Required tests from the phase spec -------------------------------

    @Test
    void test1_basicGet() {
        ParsedRequestDto r = parser.parse("curl 'https://example.com/api'");
        assertEquals("GET", r.method());
        assertEquals("https://example.com/api", r.url());
        assertEquals("", r.body());
        assertTrue(r.headers().isEmpty());
        assertTrue(r.cookies().isEmpty());
    }

    @Test
    void test2_headers() {
        String cmd = "curl 'https://example.com' \\\n"
                + "  -H 'accept: application/json' \\\n"
                + "  -H 'authorization: Bearer abc123'";
        ParsedRequestDto r = parser.parse(cmd);
        assertEquals(
                List.of(new HeaderDto("accept", "application/json"),
                        new HeaderDto("authorization", "Bearer abc123")),
                r.headers());
    }

    @Test
    void test3_cookies() {
        ParsedRequestDto r = parser.parse("curl 'https://example.com' -b 'session=abc123; theme=dark'");
        assertEquals(
                List.of(new CookieDto("session", "abc123"), new CookieDto("theme", "dark")),
                r.cookies());
    }

    @Test
    void test4_postBody() {
        String cmd = "curl 'https://example.com/api' \\\n"
                + "  -H 'content-type: application/json' \\\n"
                + "  --data-raw '{\"name\":\"William\"}'";
        ParsedRequestDto r = parser.parse(cmd);
        assertEquals("POST", r.method());
        assertEquals("{\"name\":\"William\"}", r.body());
    }

    @Test
    void test5_spacesInsideValue() {
        ParsedRequestDto r = parser.parse("curl 'https://example.com' -H 'X-Test: hello world'");
        assertEquals("hello world", r.headers().get(0).value());
    }

    @Test
    void test6_colonInsideHeaderValue() {
        ParsedRequestDto r = parser.parse("curl 'https://example.com' -H 'Authorization: Basic abc:def:ghi'");
        assertEquals("Authorization", r.headers().get(0).key());
        assertEquals("Basic abc:def:ghi", r.headers().get(0).value());
    }

    @Test
    void test7_cookieValueContainingEquals() {
        ParsedRequestDto r = parser.parse("curl 'https://example.com' -b 'token=abc=def=ghi'");
        assertEquals(new CookieDto("token", "abc=def=ghi"), r.cookies().get(0));
    }

    @Test
    void test8_realisticChromeMultilineCommand() {
        String cmd = "curl 'https://example.com/api/user' \\\n"
                + "  -H 'accept: application/json' \\\n"
                + "  -H 'authorization: Bearer abc123' \\\n"
                + "  -H 'content-type: application/json' \\\n"
                + "  -H 'x-test: hello' \\\n"
                + "  -b 'session=xyz789; theme=dark' \\\n"
                + "  --data-raw '{\"name\":\"William\"}' \\\n"
                + "  --compressed";
        ParsedRequestDto r = parser.parse(cmd);

        assertEquals("POST", r.method());
        assertEquals("https://example.com/api/user", r.url());
        assertEquals(4, r.headers().size());
        assertEquals(new HeaderDto("accept", "application/json"), r.headers().get(0));
        assertEquals(new HeaderDto("x-test", "hello"), r.headers().get(3));
        assertEquals(
                List.of(new CookieDto("session", "xyz789"), new CookieDto("theme", "dark")),
                r.cookies());
        assertEquals("{\"name\":\"William\"}", r.body());
    }

    // ---- Extra coverage --------------------------------------------------

    @Test
    void doubleQuotedUrl() {
        ParsedRequestDto r = parser.parse("curl \"https://example.com/api\"");
        assertEquals("https://example.com/api", r.url());
    }

    @Test
    void explicitMethodShortForm() {
        ParsedRequestDto r = parser.parse("curl -X DELETE 'https://example.com/x'");
        assertEquals("DELETE", r.method());
    }

    @Test
    void explicitMethodLongForm() {
        ParsedRequestDto r = parser.parse("curl --request PUT 'https://example.com/x'");
        assertEquals("PUT", r.method());
    }

    @Test
    void explicitMethodWinsOverBodyDefault() {
        ParsedRequestDto r = parser.parse("curl -X PATCH 'https://example.com' --data 'a=1'");
        assertEquals("PATCH", r.method());
        assertEquals("a=1", r.body());
    }

    @Test
    void dataOptionAliasesAndDefaultPostMethod() {
        assertEquals("POST", parser.parse("curl 'https://e.com' -d 'a=1'").method());
        assertEquals("POST", parser.parse("curl 'https://e.com' --data 'a=1'").method());
        assertEquals("POST", parser.parse("curl 'https://e.com' --data-binary 'a=1'").method());
    }

    @Test
    void multipleDataArgsAreJoinedWithAmpersand() {
        ParsedRequestDto r = parser.parse("curl 'https://e.com' -d 'a=1' -d 'b=2'");
        assertEquals("a=1&b=2", r.body());
    }

    @Test
    void headerOptionLongForm() {
        ParsedRequestDto r = parser.parse("curl 'https://e.com' --header 'Accept: application/json'");
        assertEquals(new HeaderDto("Accept", "application/json"), r.headers().get(0));
    }

    @Test
    void cookieOptionLongFormAndWhitespace() {
        ParsedRequestDto r = parser.parse("curl 'https://e.com' --cookie 'a=1;   b=2 ;c=3'");
        assertEquals(
                List.of(new CookieDto("a", "1"), new CookieDto("b", "2"), new CookieDto("c", "3")),
                r.cookies());
    }

    @Test
    void cookieHeaderIsRedirectedIntoCookiesList() {
        ParsedRequestDto r = parser.parse("curl 'https://e.com' -H 'Cookie: a=1; b=2'");
        assertEquals(List.of(new CookieDto("a", "1"), new CookieDto("b", "2")), r.cookies());
        assertTrue(r.headers().isEmpty());
    }

    @Test
    void compressedIsIgnoredSilently() {
        ParsedRequestDto r = parser.parse("curl 'https://e.com' --compressed");
        assertEquals("GET", r.method());
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    void insecureIsIgnoredWithAWarning() {
        ParsedRequestDto r = parser.parse("curl 'https://e.com' -k");
        assertEquals(1, r.warnings().size());
        assertTrue(r.warnings().get(0).toLowerCase().contains("tls"));
    }

    @Test
    void missingUrlFails() {
        CurlParseException ex = assertThrows(CurlParseException.class,
                () -> parser.parse("curl -H 'a: b'"));
        assertTrue(ex.getMessage().toLowerCase().contains("url"));
    }

    @Test
    void unsupportedFormOptionFails() {
        CurlParseException ex = assertThrows(CurlParseException.class,
                () -> parser.parse("curl 'https://e.com' -F 'file=@photo.png'"));
        assertTrue(ex.getMessage().contains("-F"));
    }

    @Test
    void emptyInputFails() {
        assertThrows(CurlParseException.class, () -> parser.parse("   "));
    }

    @Test
    void ansiCQuotedBodyWithApostrophe() {
        // Chrome emits $'...' when the body contains a single quote.
        ParsedRequestDto r = parser.parse("curl 'https://e.com' --data-raw $'{\"msg\":\"it\\'s ok\"}'");
        assertEquals("{\"msg\":\"it's ok\"}", r.body());
        assertEquals("POST", r.method());
    }

    @Test
    void urlCanComeBeforeOptions() {
        ParsedRequestDto r = parser.parse("curl https://example.com/api -H 'a: b'");
        assertEquals("https://example.com/api", r.url());
        assertEquals(1, r.headers().size());
    }

    @Test
    void optionEqualsValueForm() {
        ParsedRequestDto r = parser.parse("curl 'https://e.com' --request=POST --data-raw='x=1'");
        assertEquals("POST", r.method());
        assertEquals("x=1", r.body());
    }
}
