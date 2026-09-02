package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for POSIX single-quote escaping. */
class ShellQuoteTest {

    @Test
    void wrapsPlainTextInSingleQuotes() {
        assertEquals("'abc'", ShellQuote.single("abc"));
    }

    @Test
    void emptyStringBecomesEmptyQuotes() {
        assertEquals("''", ShellQuote.single(""));
        assertEquals("''", ShellQuote.single(null));
    }

    @Test
    void spacesStayInsideOneQuotedArgument() {
        assertEquals("'hello world'", ShellQuote.single("hello world"));
    }

    @Test
    void doubleQuotesNeedNoEscapingInsideSingleQuotes() {
        assertEquals("'say \"hi\"'", ShellQuote.single("say \"hi\""));
    }

    @Test
    void singleQuoteIsClosedEscapedAndReopened() {
        assertEquals("'hello'\\''world'", ShellQuote.single("hello'world"));
    }

    @Test
    void shellMetacharactersAreLiteral() {
        assertEquals("'$(rm -rf /)'", ShellQuote.single("$(rm -rf /)"));
        assertEquals("'a && b; c | d'", ShellQuote.single("a && b; c | d"));
        assertEquals("'`whoami`'", ShellQuote.single("`whoami`"));
    }

    @Test
    void colonEqualsAndJsonAreLiteral() {
        assertEquals("'abc:def'", ShellQuote.single("abc:def"));
        assertEquals("'abc=def'", ShellQuote.single("abc=def"));
        assertEquals("'{\"test\":\"hello world\"}'", ShellQuote.single("{\"test\":\"hello world\"}"));
    }
}
