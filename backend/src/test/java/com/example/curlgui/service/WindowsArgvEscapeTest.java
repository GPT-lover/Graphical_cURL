package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure encoding logic ({@link WindowsArgvEscape#encode}) -
 * OS-independent, so these run on every platform regardless of where the
 * bug they guard against (Windows-only) actually manifests. See
 * {@code CurlProcessExecutorWindowsArgvTest} for the empirical, real-process
 * proof this convention actually round-trips through the JDK's {@code
 * ProcessBuilder} on Windows.
 */
class WindowsArgvEscapeTest {

    @Test
    void plainTextIsLeftUnquoted() {
        assertEquals("plain-no-special-chars", WindowsArgvEscape.encode("plain-no-special-chars"));
    }

    @Test
    void emptyStringBecomesEmptyQuotes() {
        assertEquals("\"\"", WindowsArgvEscape.encode(""));
        assertEquals("\"\"", WindowsArgvEscape.encode(null));
    }

    @Test
    void aSpaceTriggersQuotingWithNoOtherChanges() {
        assertEquals("\"hello world\"", WindowsArgvEscape.encode("hello world"));
    }

    @Test
    void windowsPathWithSpaceIsQuotedWithoutDoublingOrdinaryBackslashes() {
        assertEquals("\"C:\\Users\\Yajun Wu\\Downloads\\IMAGE.webp\"",
                WindowsArgvEscape.encode("C:\\Users\\Yajun Wu\\Downloads\\IMAGE.webp"));
    }

    /** The exact field that broke: a Next.js Server Action's JSON args array. */
    @Test
    void bareDoubleQuoteWithNoWhitespaceStillTriggersQuoting() {
        assertEquals("\"0=[\\\"$K1\\\"]\"", WindowsArgvEscape.encode("0=[\"$K1\"]"));
    }

    @Test
    void aSingleEmbeddedQuoteIsEscapedWithOneBackslash() {
        assertEquals("\"say \\\"hi\\\"\"", WindowsArgvEscape.encode("say \"hi\""));
    }

    @Test
    void trailingBackslashIsDoubledBeforeTheClosingQuote() {
        assertEquals("\"trailing backslash\\\\\"", WindowsArgvEscape.encode("trailing backslash\\"));
    }

    @Test
    void backslashesImmediatelyBeforeAnEmbeddedQuoteAreDoubled() {
        assertEquals("\"already\\\\\\\"escaped\\\\\\\"pair\"",
                WindowsArgvEscape.encode("already\\\"escaped\\\"pair"));
    }

    @Test
    void ordinaryBackslashesNotAdjacentToAQuoteAreUntouchedWhenNoQuotingIsNeeded() {
        // No space and no '"' anywhere - left completely alone.
        assertEquals("C:\\Users\\Alex\\photo.jpg", WindowsArgvEscape.encode("C:\\Users\\Alex\\photo.jpg"));
    }

    @Test
    void dollarSignsAndBracketsNeedNoEscapingOfTheirOwn() {
        assertEquals("[$K1]", WindowsArgvEscape.encode("[$K1]"));
    }

    @Test
    void forCurrentOsIsAnIdentityFunctionUnlessRunningOnWindows() {
        String value = "0=[\"$K1\"]";
        String result = WindowsArgvEscape.forCurrentOs(value);
        if (WindowsArgvEscape.isWindows()) {
            assertEquals(WindowsArgvEscape.encode(value), result);
        } else {
            assertEquals(value, result);
        }
    }
}
