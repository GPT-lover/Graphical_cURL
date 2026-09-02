package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
