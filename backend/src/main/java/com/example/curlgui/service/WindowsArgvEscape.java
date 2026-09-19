package com.example.curlgui.service;

/**
 * Pre-encodes one {@link ProcessBuilder} argv element so it survives Windows'
 * {@code CreateProcess}, which - unlike POSIX {@code execve} - takes a single
 * command-line string rather than a real argv array. The JDK re-serialises
 * {@code ProcessBuilder}'s {@code List<String>} into that string itself, and
 * its own re-serialisation only wraps an argument in quotes when it contains
 * a space or a tab - it never escapes an embedded {@code "}. A bare {@code "}
 * that reaches curl.exe (or any normally-argv-parsing child process) always
 * toggles quoted mode and is stripped, <b>whether or not the argument also
 * has whitespace</b> - confirmed by hand: a {@code --form-string} value of
 * {@code ["$K1"]} (an unremarkable JSON array - the kind of payload a Next.js
 * Server Action sends for a file-upload argument) arrived at the server as
 * {@code [$K1]}, breaking its own {@code JSON.parse} and producing a 500.
 *
 * <p>The fix is to pre-encode any argument containing a space, tab or {@code
 * "} ourselves, using the exact backslash-doubling convention a normal
 * argv-parsing Windows child process (curl.exe included) expects, and wrap it
 * in our own literal quotes. The JDK recognises an argument that already
 * starts and ends with {@code "} as pre-quoted by the caller and passes it
 * through unchanged, so our encoding - and nothing else - is what the child
 * process's argv parser sees (verified by hand against a real child process on
 * this platform, including paths with spaces, values with embedded quotes,
 * trailing backslashes and combinations of all three).
 *
 * <p>A no-op on every other OS: POSIX {@code ProcessBuilder} hands argv to
 * {@code execve} byte-for-byte, so there is no re-serialisation step to work
 * around.
 */
final class WindowsArgvEscape {

    private WindowsArgvEscape() {
    }

    /** Encode {@code arg} for {@link ProcessBuilder} on Windows; returned as-is elsewhere. */
    static String forCurrentOs(String arg) {
        return isWindows() ? encode(arg) : arg;
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    /** The actual Windows argv-quoting convention; package-visible so it can be unit-tested directly. */
    static String encode(String arg) {
        if (arg == null || arg.isEmpty()) {
            return "\"\"";
        }
        if (!needsQuoting(arg)) {
            return arg;
        }
        StringBuilder sb = new StringBuilder(arg.length() + 2);
        sb.append('"');
        int backslashes = 0;
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == '\\') {
                backslashes++;
            } else if (c == '"') {
                appendBackslashes(sb, backslashes * 2 + 1);
                sb.append('"');
                backslashes = 0;
            } else {
                appendBackslashes(sb, backslashes);
                backslashes = 0;
                sb.append(c);
            }
        }
        // A run of backslashes right before our own closing quote must be
        // doubled too, or it would escape that quote instead of ending the argument.
        appendBackslashes(sb, backslashes * 2);
        sb.append('"');
        return sb.toString();
    }

    private static boolean needsQuoting(String arg) {
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == ' ' || c == '\t' || c == '"') {
                return true;
            }
        }
        return false;
    }

    private static void appendBackslashes(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) {
            sb.append('\\');
        }
    }
}
