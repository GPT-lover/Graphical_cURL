package com.example.curlgui.service;

/**
 * Builds the value string for one curl {@code -F}/{@code --form-string}
 * argument - curl's <em>own</em> micro-syntax for a form field.
 *
 * <p>Two variants, for two very different consumers:
 * <ul>
 *   <li>{@link #forExecFile} - used by {@link CurlCommandBuilder}, which hands
 *       argv straight to {@link ProcessBuilder}. <b>Unquoted.</b></li>
 *   <li>{@link #forGeneratedFile} - used by {@link CurlGeneratorService} to
 *       produce POSIX-shell text (further wrapped in {@link
 *       ShellQuote#single}). <b>Quoted</b>, using curl's own escaping.</li>
 * </ul>
 *
 * <h3>Why these must differ (a real bug found in manual testing)</h3>
 * curl's quoted-path syntax - {@code name=@"path"}, backslash-escaping only an
 * immediately following {@code "} or {@code \} - is exactly what a POSIX shell
 * later hands to curl unchanged (inside the surrounding {@code '...'} from
 * {@link ShellQuote}, nothing is reinterpreted, so curl's own parser sees
 * exactly what {@link #forGeneratedFile} wrote).
 *
 * <p>{@link CurlCommandBuilder}'s argv does <b>not</b> go through a shell, but
 * on Windows it still isn't delivered byte-for-byte: {@code ProcessBuilder}
 * must serialise the arg list back into the single command-line string
 * {@code CreateProcess} takes, and re-quotes/re-escapes any argument
 * containing a space or a {@code "} per the Win32 argv convention - which
 * collides with backslashes and quotes {@code MultipartFormValue} itself
 * already added, corrupting the path curl.exe's own argv-decoding
 * reconstructs (confirmed by hand: the quoted form silently truncated a path
 * containing a space and uploaded an unrelated file). The unquoted form has
 * nothing for that re-quoting step to collide with, and {@code
 * ProcessBuilder} already delivers spaces/backslashes in an argv entry
 * intact - exactly what real curl then reads correctly up to the first
 * {@code ;}/{@code ,}. A path containing a literal {@code ;} or {@code ,}
 * (which curl's own grammar would misread as a parameter separator) can't be
 * expressed this way; {@code RequestService} rejects those up front with a
 * clear error rather than risk corrupting the request.
 */
final class MultipartFormValue {

    private MultipartFormValue() {
    }

    /**
     * {@code name=@path[;type=mime]}, unquoted, for direct {@code
     * ProcessBuilder} execution - see class docs for why quoting must NOT be
     * used here. The caller must already have rejected paths containing
     * {@code ;} or {@code ,} (curl would otherwise misread one as the start
     * of a {@code ;type=}/{@code ;filename=} parameter).
     */
    static String forExecFile(String name, String path, String contentType) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=@").append(path == null ? "" : path);
        if (contentType != null && !contentType.isBlank()) {
            sb.append(";type=").append(contentType.trim());
        }
        return sb.toString();
    }

    /**
     * {@code name=@"path"[;type=mime]}, curl-quoted, for generated POSIX-shell
     * text ({@link CurlGeneratorService}) - safe there because a real shell
     * later hands curl the quoted text unchanged (see class docs).
     */
    static String forGeneratedFile(String name, String path, String contentType) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=@").append(quote(path));
        if (contentType != null && !contentType.isBlank()) {
            sb.append(";type=").append(contentType.trim());
        }
        return sb.toString();
    }

    /**
     * {@code name=value} - a text field, meant for {@code --form-string} (never
     * {@code -F}) so a value starting with {@code @} or {@code <} is sent
     * literally instead of being read as curl's own file/read-from-file syntax.
     */
    static String forText(String name, String value) {
        return name + "=" + (value == null ? "" : value);
    }

    private static String quote(String raw) {
        String s = raw == null ? "" : raw;
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }
}
