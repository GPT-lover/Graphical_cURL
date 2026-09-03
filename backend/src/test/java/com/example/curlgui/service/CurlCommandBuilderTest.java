package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.CurlOptionsDto;
import com.example.curlgui.dto.HeaderDto;

/**
 * Pure tests for the curl argument-list builder. No process is spawned - these
 * assert the exact argv {@link CurlProcessExecutor} would hand to
 * {@link ProcessBuilder}.
 */
class CurlCommandBuilderTest {

    private static final int CT = CurlProcessExecutor.DEFAULT_CONNECT_TIMEOUT_SECONDS;
    private static final int MT = CurlProcessExecutor.DEFAULT_MAX_TIME_SECONDS;

    private List<String> build(String method, String url, List<HeaderDto> headers,
                               String cookie, String dataFile, CurlOptionsDto opt) {
        return CurlCommandBuilder.build("curl.exe", method, url, headers, cookie, dataFile,
                "H:/tmp/headers", "H:/tmp/body", opt, CT, MT);
    }

    /** The value that follows a given flag in an argv list (first occurrence). */
    private static String valueAfter(List<String> argv, String flag) {
        int i = argv.indexOf(flag);
        return (i >= 0 && i + 1 < argv.size()) ? argv.get(i + 1) : null;
    }

    private static boolean hasPair(List<String> argv, String flag, String value) {
        for (int i = 0; i + 1 < argv.size(); i++) {
            if (argv.get(i).equals(flag) && argv.get(i + 1).equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void testA_simpleGet() {
        List<String> argv = build("GET", "https://example.com/x", List.of(), null, null,
                CurlOptionsDto.none());

        assertEquals("curl.exe", argv.get(0));
        assertEquals("--disable", argv.get(1));          // ignore ~/.curlrc, must be first option
        assertTrue(argv.contains("--silent"));
        assertTrue(argv.contains("--show-error"));
        assertFalse(argv.contains("--include"), "must never use curl -i");
        assertFalse(argv.contains("-i"), "must never use curl -i");
        assertTrue(hasPair(argv, "--output", "H:/tmp/body"));
        assertTrue(hasPair(argv, "--dump-header", "H:/tmp/headers"));
        assertEquals(CurlCommandBuilder.WRITE_OUT_FORMAT, valueAfter(argv, "--write-out"));
        assertTrue(hasPair(argv, "--request", "GET"));
        assertTrue(hasPair(argv, "--url", "https://example.com/x"));
        assertTrue(hasPair(argv, "--connect-timeout", String.valueOf(CT)));
        assertTrue(hasPair(argv, "--max-time", String.valueOf(MT)));
        assertFalse(argv.contains("--data-binary"));
    }

    @Test
    void testB_postJsonBodyComesFromAFile() {
        List<String> argv = build("POST", "https://example.com/api",
                List.of(new HeaderDto("content-type", "application/json")),
                null, "H:/tmp/data", CurlOptionsDto.none());

        assertTrue(hasPair(argv, "--request", "POST"));
        assertTrue(hasPair(argv, "--header", "content-type: application/json"));
        // body via @file (binary-safe, no OS command-line length limit)
        assertTrue(hasPair(argv, "--data-binary", "@H:/tmp/data"));
    }

    @Test
    void testC_customHeadersSurviveInOrder() {
        List<String> argv = build("GET", "https://example.com",
                List.of(new HeaderDto("x-a", "1"),
                        new HeaderDto("x-b", "two words"),
                        new HeaderDto("accept", "text/x-component")),
                null, null, CurlOptionsDto.none());

        int a = argv.indexOf("x-a: 1");
        int b = argv.indexOf("x-b: two words");
        int c = argv.indexOf("accept: text/x-component");
        assertTrue(a > 0 && b > a && c > b, "headers kept in editor order");
    }

    @Test
    void testD_cookiesBecomeOneCookieHeaderLast() {
        List<String> argv = build("GET", "https://example.com",
                List.of(new HeaderDto("accept", "*/*")),
                "sb-auth=abc123; theme=dark", null, CurlOptionsDto.none());

        assertTrue(hasPair(argv, "--header", "Cookie: sb-auth=abc123; theme=dark"));
        // exactly one Cookie header, and it comes after the normal headers
        long cookieHeaders = argv.stream().filter(s -> s.startsWith("Cookie: ")).count();
        assertEquals(1, cookieHeaders);
        assertTrue(argv.indexOf("Cookie: sb-auth=abc123; theme=dark") > argv.indexOf("accept: */*"));
    }

    @Test
    void headModeUsesHeadNotRequestAndNoBody() {
        List<String> argv = build("HEAD", "https://example.com", List.of(), null, "H:/tmp/data",
                CurlOptionsDto.none());
        assertTrue(argv.contains("--head"));
        assertFalse(argv.contains("--request"));
        assertFalse(argv.contains("--data-binary"), "HEAD must not carry a body");
    }

    @Test
    void testF_transportOptionsFromImportedCommandArePassedThrough() {
        CurlOptionsDto opt = new CurlOptionsDto(
                true, "1.1", true, true, 5, 20, "http://127.0.0.1:8888", "user:pass");
        List<String> argv = build("POST", "https://grooves-web.vercel.app/track/1442954500",
                List.of(new HeaderDto("accept", "text/x-component")),
                "sb-token=REDACTED", "H:/tmp/data", opt);

        assertTrue(argv.contains("--compressed"));
        assertTrue(argv.contains("--http1.1"));
        assertTrue(argv.contains("--location"));
        assertTrue(argv.contains("--insecure"));
        assertTrue(hasPair(argv, "--connect-timeout", "5"));
        assertTrue(hasPair(argv, "--max-time", "20"));
        assertTrue(hasPair(argv, "--proxy", "http://127.0.0.1:8888"));
        assertTrue(hasPair(argv, "--proxy-user", "user:pass"));
        assertTrue(hasPair(argv, "--request", "POST"));
        assertTrue(hasPair(argv, "--data-binary", "@H:/tmp/data"));
        assertTrue(hasPair(argv, "--url", "https://grooves-web.vercel.app/track/1442954500"));
    }

    @Test
    void httpVersionFlagMapping() {
        assertEquals("--http1.0", CurlCommandBuilder.httpVersionFlag("1.0"));
        assertEquals("--http1.1", CurlCommandBuilder.httpVersionFlag("1.1"));
        assertEquals("--http2", CurlCommandBuilder.httpVersionFlag("2"));
        assertEquals("--http2-prior-knowledge", CurlCommandBuilder.httpVersionFlag("2-prior-knowledge"));
        assertEquals(null, CurlCommandBuilder.httpVersionFlag(null));
        assertEquals(null, CurlCommandBuilder.httpVersionFlag("bogus"));
    }

    @Test
    void aLiteralCookieHeaderRowIsNotDuplicated() {
        // If the editor still has a row literally named "Cookie", the builder
        // skips it (the combined value is passed instead).
        List<String> argv = build("GET", "https://example.com",
                List.of(new HeaderDto("Cookie", "should-not-appear")),
                "real=1", null, CurlOptionsDto.none());
        assertFalse(argv.contains("Cookie: should-not-appear"));
        assertTrue(argv.contains("Cookie: real=1"));
    }
}
