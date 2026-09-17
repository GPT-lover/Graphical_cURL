package com.example.curlgui.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.curlgui.dto.CookieDto;
import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.SendRequestDto;

/**
 * Substitutes <b>dynamic</b> templates - {@code {{random(N)}}} and {@code
 * {{increment(N)}}} - immediately before a request is sent. Pure string
 * replacement - no expressions, no shell, no code execution of any kind.
 *
 * <h3>Syntax</h3>
 * {@code \{\{ random(50) \}\}} (surrounding whitespace tolerated) produces a new
 * 50-character string drawn from {@link #ALPHABET}. Every occurrence gets its
 * own value: {@code {{random(20)}}-{{random(20)}}} yields two different strings.
 *
 * <p>{@code \{\{ increment(N) \}\}} produces {@code N} on the first send and
 * counts up by one on every subsequent iteration of whatever loop or chain is
 * sending it ({@code N}, {@code N+1}, {@code N+2}, ...). Every occurrence in
 * one request shares the same counter for a given send, so {@code
 * {{increment(1)}}-{{increment(1)}}} on iteration 3 yields {@code "3-3"}, not
 * two independently counting values.
 *
 * <h3>When it runs</h3>
 * Unlike {@link EnvironmentVariableResolver} - which resolves once, up front -
 * this resolver runs inside {@code RequestService#executeResolved}, i.e.
 * immediately before <em>each</em> curl invocation. That is what makes every
 * loop iteration, every parallel task and every chain dispatch receive its own
 * {@code random} value and the right {@code increment} value, while the stored
 * request keeps the literal template and stays reusable.
 *
 * <h3>Malformed templates</h3>
 * A {@code random} length that is not a plain number, or is outside {@code
 * 1..}{@value #MAX_LENGTH}, or an {@code increment} start that is not a plain
 * (optionally negative) whole number, raises {@link InvalidRequestException}
 * (HTTP 400) - the same fail-fast contract {@link UnresolvedVariableException}
 * follows, so nothing is sent. {@code {{random}}} / {@code {{increment}}}
 * without parentheses are <b>not</b> dynamic templates at all: they are
 * perfectly legal environment-variable names and are left for {@link
 * EnvironmentVariableResolver} to handle.
 */
@Component
public class DynamicVariableResolver {

    /** Character set for generated values - alphanumeric only, never punctuation. */
    static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** Upper bound on a requested length, so a typo can't ask for a gigabyte. */
    static final int MAX_LENGTH = 4096;

    /**
     * Matches {@code {{random(<anything but brackets>)}}}. The argument is
     * captured loosely on purpose: "abc" and "-5" must reach
     * {@link #generate} so they can be reported as a clear validation error
     * rather than silently passing through as literal text.
     */
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /** Optionally-signed whole number, for {@code increment}'s start value. */
    private static final Pattern SIGNED_DIGITS = Pattern.compile("-?\\d+");

    private static final Pattern RANDOM_TEMPLATE =
            Pattern.compile("\\{\\{\\s*random\\s*\\(([^(){}]*)\\)\\s*\\}\\}");

    private static final Pattern INCREMENT_TEMPLATE =
            Pattern.compile("\\{\\{\\s*increment\\s*\\(([^(){}]*)\\)\\s*\\}\\}");

    /** Thread-safe; shared across the loop / chain worker pools. */
    private final SecureRandom random = new SecureRandom();

    /**
     * Resolve every dynamic template in one string as iteration 1 (i.e. every
     * {@code {{increment(N)}}} resolves to its literal start value {@code N}).
     * Equivalent to {@link #resolve(String, int)} with {@code iteration = 1} -
     * the right behaviour for a plain Send, outside any loop or chain.
     */
    public String resolve(String text) {
        return resolve(text, 1);
    }

    /**
     * Resolve every dynamic template in one string, generating an independent
     * value per {@code random} occurrence and computing {@code increment}
     * occurrences from {@code iteration} (1-based: iteration 1 yields each
     * increment's literal start value). {@code null} / template-free input is
     * returned unchanged, so requests that use no dynamic variables behave
     * exactly as they did before.
     */
    public String resolve(String text, int iteration) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return resolveIncrement(resolveRandom(text), iteration);
    }

    private String resolveRandom(String text) {
        Matcher matcher = RANDOM_TEMPLATE.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        do {
            matcher.appendReplacement(out, Matcher.quoteReplacement(generate(matcher.group(1))));
        } while (matcher.find());
        matcher.appendTail(out);
        return out.toString();
    }

    private String resolveIncrement(String text, int iteration) {
        Matcher matcher = INCREMENT_TEMPLATE.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        do {
            matcher.appendReplacement(
                    out, Matcher.quoteReplacement(incrementValue(matcher.group(1), iteration)));
        } while (matcher.find());
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Build the <b>execution copy</b> of a request as iteration 1 - see {@link
     * #resolveRequest(SendRequestDto, int)}. Equivalent to that overload with
     * {@code iteration = 1}, the right behaviour for a plain Send.
     */
    public SendRequestDto resolveRequest(SendRequestDto original) {
        return resolveRequest(original, 1);
    }

    /**
     * Build the <b>execution copy</b> of a request with dynamic templates in the
     * URL (including its query string), header values, cookie values and body
     * resolved for the given 1-based {@code iteration}. The input {@code
     * original} is never modified - it keeps its templates and can be reused for
     * the next iteration. Internal metadata (method, environment id, curl
     * transport options) is left untouched.
     */
    public SendRequestDto resolveRequest(SendRequestDto original, int iteration) {
        List<HeaderDto> headers = original.headers() == null ? null : original.headers().stream()
                .map(h -> new HeaderDto(h.key(), resolve(h.value(), iteration)))
                .toList();

        List<CookieDto> cookies = original.cookies() == null ? null : original.cookies().stream()
                .map(c -> new CookieDto(c.key(), resolve(c.value(), iteration)))
                .toList();

        return new SendRequestDto(
                original.method(), resolve(original.url(), iteration), headers, cookies,
                resolve(original.body(), iteration), original.environmentId(), original.curlOptions());
    }

    /** Validate the captured length argument and produce that many characters. */
    private String generate(String rawLength) {
        int length = parseLength(rawLength);
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return value.toString();
    }

    private static int parseLength(String rawLength) {
        String trimmed = rawLength == null ? "" : rawLength.trim();
        // A number too big for an int is still a *number*, so it must be
        // reported as out-of-range rather than as "not a number".
        long length = DIGITS.matcher(trimmed).matches() ? parseDigits(trimmed) : notANumber(trimmed);
        if (length < 1 || length > MAX_LENGTH) {
            throw new InvalidRequestException(
                    "{{random(" + trimmed + ")}} is out of range: the length must be between 1 and "
                            + MAX_LENGTH + ".");
        }
        return (int) length;
    }

    /** {@code trimmed} is all digits; anything beyond the cap is clamped to "too big". */
    private static long parseDigits(String trimmed) {
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static long notANumber(String trimmed) {
        throw new InvalidRequestException(
                "{{random(...)}} needs a whole number of characters, e.g. {{random(50)}}"
                        + " (got \"" + trimmed + "\").");
    }

    /** Bound on an {@code increment} start value, well clear of overflow once an iteration count is added. */
    static final long MAX_INCREMENT_MAGNITUDE = 1_000_000_000L;

    /** Validate the captured start argument and compute this iteration's value. */
    private static String incrementValue(String rawStart, int iteration) {
        long start = parseStart(rawStart);
        return Long.toString(start + (iteration - 1L));
    }

    private static long parseStart(String rawStart) {
        String trimmed = rawStart == null ? "" : rawStart.trim();
        if (!SIGNED_DIGITS.matcher(trimmed).matches()) {
            throw new InvalidRequestException(
                    "{{increment(...)}} needs a whole number start value, e.g. {{increment(1)}}"
                            + " (got \"" + trimmed + "\").");
        }
        long start;
        try {
            start = Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            start = trimmed.startsWith("-") ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        if (start < -MAX_INCREMENT_MAGNITUDE || start > MAX_INCREMENT_MAGNITUDE) {
            throw new InvalidRequestException(
                    "{{increment(" + trimmed + ")}} is out of range: the start value must be between -"
                            + MAX_INCREMENT_MAGNITUDE + " and " + MAX_INCREMENT_MAGNITUDE + ".");
        }
        return start;
    }
}
