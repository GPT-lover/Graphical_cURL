package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.curlgui.dto.CookieDto;
import com.example.curlgui.dto.HeaderDto;

/**
 * Decides the single {@code Cookie} header for an outgoing request, from the
 * request editor's dedicated Cookies section and/or a header the user typed
 * literally named {@code Cookie}.
 *
 * <p>Pure logic, no Spring and no {@code HttpClient}, so it is unit-tested
 * directly.
 *
 * <h3>Conflict rule (documented behaviour)</h3>
 * The dedicated Cookies section wins:
 * <ul>
 *   <li>section has cookies &rarr; the header is built from the section; a
 *       manually typed {@code Cookie} header is dropped, with a warning.</li>
 *   <li>section empty &rarr; a manually typed {@code Cookie} header is sent
 *       unchanged.</li>
 *   <li>neither &rarr; no {@code Cookie} header at all.</li>
 * </ul>
 * Either way there is never more than one {@code Cookie} header.
 *
 * <h3>Within the section</h3>
 * <ul>
 *   <li>Blank-name rows are skipped (an empty editor row sends nothing).</li>
 *   <li>Names and values are trimmed.</li>
 *   <li>Values may contain {@code =} - only the pair is joined with a single
 *       {@code =}, so {@code token = abc=def} sends {@code token=abc=def}.</li>
 *   <li><b>Duplicate names:</b> the <b>last</b> occurrence wins (deterministic),
 *       with a warning. Cookie names are case-sensitive per RFC 6265, so
 *       {@code Session} and {@code session} are different cookies.</li>
 * </ul>
 */
final class CookieHeader {

    private CookieHeader() {
    }

    /**
     * @param value    the {@code Cookie} header value to send, or {@code null} to
     *                 send no {@code Cookie} header
     * @param warnings advisory messages for the response (never contains cookie
     *                 values)
     */
    record Result(String value, List<String> warnings) {
    }

    /**
     * The value of a header literally named {@code Cookie} (matched
     * case-insensitively, name trimmed), or {@code null} if there is none. If
     * several are present the last one wins - matching how {@code HttpRequest}
     * headers and shells would behave. Used by both the request executor and the
     * cURL exporter so they apply the same conflict policy.
     */
    static String extractManualCookieHeader(List<HeaderDto> headers) {
        if (headers == null) {
            return null;
        }
        String found = null;
        for (HeaderDto header : headers) {
            if (header == null || header.key() == null) {
                continue;
            }
            if (header.key().trim().equalsIgnoreCase("Cookie")) {
                found = header.value() == null ? "" : header.value();
            }
        }
        return found;
    }

    static Result resolve(List<CookieDto> sectionCookies, String manualCookieHeader) {
        List<String> warnings = new ArrayList<>();
        String fromSection = buildFromSection(sectionCookies, warnings);
        boolean hasManual = manualCookieHeader != null && !manualCookieHeader.isBlank();

        if (fromSection != null) {
            if (hasManual) {
                warnings.add("Your manually entered \"Cookie\" header was replaced by the Cookies section.");
            }
            return new Result(fromSection, warnings);
        }
        if (hasManual) {
            return new Result(manualCookieHeader.trim(), warnings);
        }
        return new Result(null, warnings);
    }

    private static String buildFromSection(List<CookieDto> cookies, List<String> warnings) {
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        // LinkedHashMap keeps the first-seen order of a name; putting the same
        // name again overwrites the value in place -> "last occurrence wins".
        Map<String, String> byName = new LinkedHashMap<>();
        for (CookieDto cookie : cookies) {
            if (cookie == null) {
                continue;
            }
            String name = cookie.key() == null ? "" : cookie.key().trim();
            if (name.isEmpty()) {
                continue; // blank editor row - not sent
            }
            String value = cookie.value() == null ? "" : cookie.value().trim();
            if (byName.containsKey(name)) {
                warnings.add("Cookie \"" + name + "\" was listed more than once; used the last value.");
            }
            byName.put(name, value);
        }
        if (byName.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        byName.forEach((name, value) -> {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(name).append('=').append(value);
        });
        return sb.toString();
    }
}
