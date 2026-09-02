package com.example.curlgui.service;

/**
 * POSIX shell quoting for a single command-line argument.
 *
 * <p>The generated cURL command targets <b>POSIX shells</b> (Git Bash, WSL,
 * macOS, Linux) - the same format Chrome's "Copy as cURL" produces. PowerShell
 * and CMD syntax are intentionally out of scope for this phase.
 *
 * <p>Strategy: wrap the whole value in single quotes and replace every embedded
 * single quote with {@code '\''} (close the quote, an escaped literal quote,
 * reopen the quote). Inside single quotes a POSIX shell treats <em>every</em>
 * other character literally - spaces, {@code " : = ; & $ `}, backslashes,
 * braces, newlines - so this one rule is safe for arbitrary text, including
 * hostile input. Never build an argument by plain concatenation.
 */
final class ShellQuote {

    private ShellQuote() {
    }

    /** Return {@code arg} as exactly one safely quoted POSIX shell argument. */
    static String single(String arg) {
        String s = arg == null ? "" : arg;
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
