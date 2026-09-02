package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.ParsedRequestDto;
import com.example.curlgui.dto.SendRequestDto;

/**
 * parse -> generate -> parse round-trip tests.
 *
 * <p>The generated command does not have to match the original text (Chrome adds
 * {@code --compressed}, browser headers, its own formatting). What must survive
 * is the request itself: method, URL, headers, cookies, body.
 */
class CurlRoundTripTest {

    private final CurlParserService parser = new CurlParserService();
    private final CurlGeneratorService generator = new CurlGeneratorService();

    /** parse a command, generate cURL from it, parse that again. */
    private ParsedRequestDto roundTrip(String curl) {
        ParsedRequestDto first = parser.parse(curl);
        SendRequestDto asRequest = new SendRequestDto(
                first.method(), first.url(), first.headers(), first.cookies(), first.body());
        String generated = generator.generate(asRequest);
        return parser.parse(generated);
    }

    private static void assertSameRequest(ParsedRequestDto expected, ParsedRequestDto actual) {
        assertEquals(expected.method(), actual.method(), "method");
        assertEquals(expected.url(), actual.url(), "url");
        assertEquals(expected.headers(), actual.headers(), "headers");
        assertEquals(expected.cookies(), actual.cookies(), "cookies");
        assertEquals(expected.body(), actual.body(), "body");
    }

    @Test
    void simpleGetRoundTrips() {
        String curl = "curl 'https://example.com/api'";
        assertSameRequest(parser.parse(curl), roundTrip(curl));
    }

    @Test
    void postWithHeadersCookiesAndBodyRoundTrips() {
        String curl = String.join("\n",
                "curl 'https://example.com/api/rate' \\",
                "  -H 'Content-Type: application/json' \\",
                "  -H 'Accept: application/json' \\",
                "  -b 'session=abc123; theme=dark' \\",
                "  --data-raw '{\"rating\":7}'");
        assertSameRequest(parser.parse(curl), roundTrip(curl));
    }

    @Test
    void realisticChromeStyleCommandRoundTrips() {
        String curl = String.join("\n",
                "curl --url 'https://api.example.com/auth/releases/test123/rate' \\",
                "  -H 'accept: application/json, text/plain, */*' \\",
                "  -H 'content-type: application/json' \\",
                "  -b 'auth=FAKE_AUTH_TOKEN; refresh=FAKE_REFRESH_TOKEN; cf_clearance=FAKE_CLEARANCE' \\",
                "  -H 'priority: u=1, i' \\",
                "  -H 'sec-ch-ua: \"Not=A?Brand\";v=\"99\", \"Google Chrome\";v=\"151\"' \\",
                "  -H 'sec-ch-ua-platform: \"Windows\"' \\",
                "  --data-raw '{\"rating\":7}' \\",
                "  --compressed");
        ParsedRequestDto original = parser.parse(curl);
        ParsedRequestDto after = roundTrip(curl);
        assertSameRequest(original, after);
        assertEquals("POST", after.method());
        assertEquals("{\"rating\":7}", after.body());
        assertEquals(5, after.headers().size());
        assertEquals(3, after.cookies().size());
    }

    @Test
    void headerAndCookieValuesWithTrickyCharactersRoundTrip() {
        String curl = String.join("\n",
                "curl 'https://example.com/x?q=hello world&n=2' \\",
                "  -H 'X-Test: hello world' \\",
                "  -H 'Authorization: Basic abc:def:ghi' \\",
                "  -b 'token=abc=def=ghi; other=plain' \\",
                "  --data-raw '{\"message\":\"hello \\\"world\\\"\"}'");
        assertSameRequest(parser.parse(curl), roundTrip(curl));
    }

    @Test
    void everyMethodRoundTrips() {
        for (String m : new String[]{"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}) {
            String curl = "curl -X " + m + " 'https://example.com/x'";
            assertEquals(m, roundTrip(curl).method(), m);
        }
    }
}
