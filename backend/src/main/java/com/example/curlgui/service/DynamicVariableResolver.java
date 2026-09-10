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
 * Substitutes <b>dynamic</b> {@code {{random(N)}}} templates with a freshly
 * generated alphanumeric string of length {@code N}. Pure string replacement -
 * no expressions, no shell, no code execution of any kind.
 *
 * <h3>Syntax</h3>
 * {@code \{\{ random(50) \}\}} (surrounding whitespace tolerated) produces a new
 * 50-character string drawn from {@link #ALPHABET}. Every occurrence gets its
 * own value: {@code {{random(20)}}-{{random(20)}}} yields two different strings.
 *
 * <h3>When it runs</h3>
 * Unlike {@link EnvironmentVariableResolver} - which resolves once, up front -
 * this resolver runs inside {@code RequestService#executeResolved}, i.e.
 * immediately before <em>each</em> curl invocation. That is what makes every
 * loop iteration, every parallel task and every chain dispatch receive its own
 * value, while the stored request keeps the literal {@code {{random(50)}}} and
 * stays reusable.
 *
 * <h3>Malformed templates</h3>
 * A length that is not a plain number, or is outside {@code 1..}{@value
 * #MAX_LENGTH}, raises {@link InvalidRequestException} (HTTP 400) - the same
 * fail-fast contract {@link UnresolvedVariableException} follows, so nothing is
 * sent. {@code {{random}}} without parentheses is <b>not</b> a dynamic template
 * at all: it is a perfectly legal environment-variable name and is left for
 * {@link EnvironmentVariableResolver} to handle.
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

    private static final Pattern RANDOM_TEMPLATE =
            Pattern.compile("\\{\\{\\s*random\\s*\\(([^(){}]*)\\)\\s*\\}\\}");

    /** Thread-safe; shared across the loop / chain worker pools. */
    private final SecureRandom random = new SecureRandom();

    /**
     * Resolve every dynamic template in one string, generating an independent
     * value per occurrence. {@code null} / template-free input is returned
     * unchanged, so requests that use no dynamic variables behave exactly as
     * they did before.
     */
    public String resolve(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
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

    /**
     * Build the <b>execution copy</b> of a request with dynamic templates in the
     * URL (including its query string), header values, cookie values and body
     * resolved. The input {@code original} is never modified - it keeps its
     * templates and can be reused for the next iteration. Internal metadata
     * (method, environment id, curl transport options) is left untouched.
     */
    public SendRequestDto resolveRequest(SendRequestDto original) {
        List<HeaderDto> headers = original.headers() == null ? null : original.headers().stream()
                .map(h -> new HeaderDto(h.key(), resolve(h.value())))
                .toList();

        List<CookieDto> cookies = original.cookies() == null ? null : original.cookies().stream()
                .map(c -> new CookieDto(c.key(), resolve(c.value())))
                .toList();

        return new SendRequestDto(
                original.method(), resolve(original.url()), headers, cookies,
                resolve(original.body()), original.environmentId(), original.curlOptions());
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
}
