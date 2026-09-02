package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a cURL command string into argument tokens the way a POSIX shell would
 * - <em>without</em> running a shell. This is step 1 of parsing; {@link
 * CurlParserService} then interprets the tokens.
 *
 * <p>Why not {@code command.split(" ")}? Because that shreds any argument that
 * contains a space, and cURL commands are full of them:
 * {@code -H 'X-Test: hello world'} must come out as the single token
 * {@code X-Test: hello world}.
 *
 * <h3>Supported quoting subset</h3>
 * <ul>
 *   <li><b>'single quotes'</b> - everything inside is literal, no escapes.</li>
 *   <li><b>"double quotes"</b> - literal, except a backslash before one of
 *       " backslash $ backtick, and a backslash-newline (line continuation).</li>
 *   <li><b>$'ANSI-C quotes'</b> - Chrome uses these when a value contains a
 *       single quote or a control char. Interprets the common letter escapes
 *       (n, t, r, b, f, v, a, e), quote/backslash escapes, and hex/unicode
 *       escapes of the form backslash-x-HH and backslash-u-HHHH.</li>
 *   <li><b>backslash</b> outside quotes escapes the next character; a trailing
 *       backslash before a newline is a line continuation.</li>
 *   <li><b>caret {@code ^}</b> before a newline - Windows {@code cmd} line
 *       continuation.</li>
 * </ul>
 *
 * <h3>Not supported</h3>
 * Variable expansion ({@code $VAR}), command substitution ({@code $(...)}),
 * globbing. Chrome's "Copy as cURL" never emits these.
 */
final class CurlTokenizer {

    private CurlTokenizer() {
    }

    private enum State { NORMAL, SINGLE, DOUBLE, ANSI_C }

    static List<String> tokenize(String input) {
        if (input == null) {
            throw new CurlParseException("No cURL command was provided.");
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // Distinguishes an empty quoted token ("") from "no token collected yet".
        boolean tokenStarted = false;
        State state = State.NORMAL;

        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);

            if (state == State.SINGLE) {
                if (c == '\'') {
                    state = State.NORMAL;
                } else {
                    current.append(c);
                }
                i++;
                continue;
            }

            if (state == State.DOUBLE) {
                if (c == '"') {
                    state = State.NORMAL;
                    i++;
                } else if (c == '\\' && i + 1 < n) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\' || next == '$' || next == '`') {
                        current.append(next);
                        i += 2;
                    } else if (next == '\n') {
                        i += 2; // line continuation inside double quotes
                    } else if (next == '\r' && i + 2 < n && input.charAt(i + 2) == '\n') {
                        i += 3;
                    } else {
                        current.append(c); // keep the lone backslash literally
                        i++;
                    }
                } else {
                    current.append(c);
                    i++;
                }
                continue;
            }

            if (state == State.ANSI_C) {
                if (c == '\'') {
                    state = State.NORMAL;
                    i++;
                } else if (c == '\\' && i + 1 < n) {
                    i = appendAnsiCEscape(input, i, current);
                } else {
                    current.append(c);
                    i++;
                }
                continue;
            }

            // state == NORMAL
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f') {
                if (tokenStarted) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    tokenStarted = false;
                }
                i++;
            } else if (c == '\'') {
                state = State.SINGLE;
                tokenStarted = true;
                i++;
            } else if (c == '"') {
                state = State.DOUBLE;
                tokenStarted = true;
                i++;
            } else if (c == '$' && i + 1 < n && input.charAt(i + 1) == '\'') {
                state = State.ANSI_C;
                tokenStarted = true;
                i += 2;
            } else if (c == '\\') {
                if (i + 1 >= n) {
                    i++; // trailing backslash - ignore
                } else {
                    char next = input.charAt(i + 1);
                    if (next == '\n') {
                        i += 2; // line continuation
                    } else if (next == '\r' && i + 2 < n && input.charAt(i + 2) == '\n') {
                        i += 3;
                    } else if (next == '\r') {
                        i += 2;
                    } else {
                        current.append(next); // escaped literal character
                        tokenStarted = true;
                        i += 2;
                    }
                }
            } else if (c == '^' && i + 1 < n
                    && (input.charAt(i + 1) == '\n' || input.charAt(i + 1) == '\r')) {
                i++; // Windows cmd caret line continuation - drop the caret
            } else {
                current.append(c);
                tokenStarted = true;
                i++;
            }
        }

        if (state != State.NORMAL) {
            throw new CurlParseException("The cURL command has an unterminated quote.");
        }
        if (tokenStarted) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /** Interpret one {@code \x} escape inside {@code $'...'}; returns the new index. */
    private static int appendAnsiCEscape(String input, int i, StringBuilder current) {
        char e = input.charAt(i + 1);
        switch (e) {
            case 'n' -> current.append('\n');
            case 't' -> current.append('\t');
            case 'r' -> current.append('\r');
            case 'b' -> current.append('\b');
            case 'f' -> current.append('\f');
            case 'v' -> current.append((char) 0x0B);
            case 'a' -> current.append((char) 0x07);
            case 'e', 'E' -> current.append((char) 0x1B);
            case '\\' -> current.append('\\');
            case '\'' -> current.append('\'');
            case '"' -> current.append('"');
            case '?' -> current.append('?');
            case '`' -> current.append('`');
            case '$' -> current.append('$');
            case 'x' -> {
                return appendHex(input, i + 2, 2, current, i);
            }
            case 'u' -> {
                return appendHex(input, i + 2, 4, current, i);
            }
            case 'U' -> {
                return appendHex(input, i + 2, 8, current, i);
            }
            default -> current.append('\\').append(e); // unknown escape: keep literally
        }
        return i + 2;
    }

    private static int appendHex(String input, int start, int maxDigits, StringBuilder current,
                                 int backslashIndex) {
        int n = input.length();
        int end = start;
        while (end < n && end - start < maxDigits && isHex(input.charAt(end))) {
            end++;
        }
        if (end == start) {
            // No hex digits after the x/u escape - emit the letter literally, don't crash.
            current.append(input.charAt(backslashIndex + 1));
            return backslashIndex + 2;
        }
        current.appendCodePoint(Integer.parseInt(input.substring(start, end), 16));
        return end;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
