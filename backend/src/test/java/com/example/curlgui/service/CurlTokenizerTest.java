package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the shell-style tokenizer (no Spring, no network).
 * The parser's correctness depends entirely on this splitting cleanly.
 */
class CurlTokenizerTest {

    @Test
    void splitsOnUnquotedWhitespace() {
        assertEquals(List.of("curl", "-X", "POST", "https://example.com"),
                CurlTokenizer.tokenize("curl -X POST https://example.com"));
    }

    @Test
    void keepsSpacesInsideSingleQuotes() {
        assertEquals(List.of("-H", "X-Test: hello world"),
                CurlTokenizer.tokenize("-H 'X-Test: hello world'"));
    }

    @Test
    void keepsSpacesInsideDoubleQuotes() {
        assertEquals(List.of("-H", "X-Test: hello world"),
                CurlTokenizer.tokenize("-H \"X-Test: hello world\""));
    }

    @Test
    void singleQuotesAreLiteral() {
        // No escape processing inside single quotes.
        assertEquals(List.of("a\\nb"), CurlTokenizer.tokenize("'a\\nb'"));
    }

    @Test
    void backslashNewlineIsLineContinuation() {
        String input = "curl 'https://example.com' \\\n  -H 'a: b'";
        assertEquals(List.of("curl", "https://example.com", "-H", "a: b"),
                CurlTokenizer.tokenize(input));
    }

    @Test
    void doubleQuoteEscapes() {
        assertEquals(List.of("say \"hi\""), CurlTokenizer.tokenize("\"say \\\"hi\\\"\""));
    }

    @Test
    void ansiCQuotingHandlesEscapedSingleQuote() {
        // $'it\'s' -> it's
        assertEquals(List.of("it's"), CurlTokenizer.tokenize("$'it\\'s'"));
    }

    @Test
    void ansiCQuotingHandlesUnicodeEscape() {
        assertEquals(List.of("A"), CurlTokenizer.tokenize("$'\\u0041'"));
    }

    @Test
    void emptyQuotedStringIsAToken() {
        assertEquals(List.of("-H", ""), CurlTokenizer.tokenize("-H ''"));
    }

    @Test
    void unterminatedQuoteThrows() {
        assertThrows(CurlParseException.class, () -> CurlTokenizer.tokenize("curl 'https://example.com"));
    }

    // --- --url must not be glued to its value ---------------------------

    @Test
    void urlOptionAndValueAreSeparateTokens() {
        assertEquals(
                List.of("curl", "--url", "https://api.example.com/x/y", "-H", "a: b"),
                CurlTokenizer.tokenize("curl --url 'https://api.example.com/x/y' -H 'a: b'"));
    }

    @Test
    void bothQuoteStylesStripOnlyTheOuterQuotes() {
        assertEquals(List.of("--url", "https://example.com/test"),
                CurlTokenizer.tokenize("--url 'https://example.com/test'"));
        assertEquals(List.of("--url", "https://example.com/test"),
                CurlTokenizer.tokenize("--url \"https://example.com/test\""));
    }

    /**
     * The realistic Chrome "Copy as cURL" shape (safe fake values). This is the
     * "show what the tokenizer produces" step: every argument comes out as
     * exactly one clean token, no line-continuation debris.
     */
    @Test
    void fullChromeStyleCommandTokenizesCleanly() {
        String cmd = String.join("\n",
                "curl --url 'https://api.example.com/auth/releases/test123/rate' \\",
                "  -H 'accept: application/json, text/plain, */*' \\",
                "  -H 'content-type: application/json' \\",
                "  -b 'auth=FAKE_AUTH_TOKEN; refresh=FAKE_REFRESH_TOKEN; cf_clearance=FAKE_CLEARANCE' \\",
                "  -H 'priority: u=1, i' \\",
                "  -H 'sec-ch-ua: \"Not=A?Brand\";v=\"99\", \"Google Chrome\";v=\"151\", \"Chromium\";v=\"151\"' \\",
                "  --data-raw '{\"rating\":7}'");

        List<String> t = CurlTokenizer.tokenize(cmd);

        // The critical assertion: --url and the URL are two separate tokens.
        assertEquals("curl", t.get(0));
        assertEquals("--url", t.get(1));
        assertEquals("https://api.example.com/auth/releases/test123/rate", t.get(2));

        // Header value with spaces + commas survives as one token.
        assertTrue(t.contains("accept: application/json, text/plain, */*"));
        assertTrue(t.contains("priority: u=1, i"));

        // The inner double quotes are part of the header value, they do NOT end
        // the outer single-quoted argument.
        assertTrue(t.contains(
                "sec-ch-ua: \"Not=A?Brand\";v=\"99\", \"Google Chrome\";v=\"151\", \"Chromium\";v=\"151\""));

        // Cookie string is one token; JSON body is one token (not split on : or ,).
        assertTrue(t.contains("auth=FAKE_AUTH_TOKEN; refresh=FAKE_REFRESH_TOKEN; cf_clearance=FAKE_CLEARANCE"));
        assertEquals("--data-raw", t.get(t.size() - 2));
        assertEquals("{\"rating\":7}", t.get(t.size() - 1));

        // No leftover backslashes or newlines from the line continuations.
        for (String tok : t) {
            assertFalse(tok.contains("\n"), "token contains a newline: " + tok);
            assertFalse(tok.contains("\r"), "token contains a CR: " + tok);
            assertFalse(tok.endsWith("\\"), "token ends with a backslash: " + tok);
        }
    }

    @Test
    void handlesCrlfLineContinuations() {
        // Windows clipboards often paste \r\n line endings.
        String cmd = "curl --url 'https://example.com/x' \\\r\n"
                + "  -H 'accept: application/json' \\\r\n"
                + "  --data-raw '{\"rating\":7}'";
        assertEquals(
                List.of("curl", "--url", "https://example.com/x",
                        "-H", "accept: application/json",
                        "--data-raw", "{\"rating\":7}"),
                CurlTokenizer.tokenize(cmd));
    }
}
