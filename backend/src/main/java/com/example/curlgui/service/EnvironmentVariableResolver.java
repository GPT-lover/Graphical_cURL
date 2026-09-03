package com.example.curlgui.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.curlgui.dto.CookieDto;
import com.example.curlgui.dto.HeaderDto;
import com.example.curlgui.dto.SendRequestDto;

/**
 * Substitutes {@code {{NAME}}} placeholders with values from the active
 * environment. Pure string replacement - no expressions, no code execution, no
 * recursion.
 *
 * <h3>Syntax</h3>
 * {@code \{\{ NAME \}\}} where NAME is letters, digits, {@code _}, {@code .} or
 * {@code -} (optional surrounding whitespace is tolerated). Header <b>names</b>
 * and cookie <b>names</b> are never substituted - only values, the URL and the
 * body.
 *
 * <h3>Rules</h3>
 * <ul>
 *   <li>known name -> replaced with its value (which may be the empty string)</li>
 *   <li>unknown name -> collected; when any remain, {@link
 *       UnresolvedVariableException} is thrown and nothing is sent</li>
 *   <li>a value that itself contains {@code {{...}}} is <b>not</b> re-scanned
 *       (single pass, no recursion)</li>
 * </ul>
 */
@Component
public class EnvironmentVariableResolver {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*\\}\\}");

    /**
     * Resolve one string. Unknown names are added to {@code unknownSink} rather
     * than thrown, so a caller can collect every unknown across a whole request
     * and report them together.
     */
    public String resolve(String text, Map<String, String> variables, Set<String> unknownSink) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement;
            if (variables.containsKey(name)) {
                replacement = variables.get(name) == null ? "" : variables.get(name);
            } else {
                unknownSink.add(name);
                replacement = matcher.group(0); // leave the placeholder for now
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Convenience: resolve one string, throwing immediately on any unknown name. */
    public String resolve(String text, Map<String, String> variables) {
        Set<String> unknown = new LinkedHashSet<>();
        String result = resolve(text, variables, unknown);
        if (!unknown.isEmpty()) {
            throw new UnresolvedVariableException(unknown);
        }
        return result;
    }

    /**
     * Build the <b>execution copy</b> of a request with all placeholders in the
     * URL, header values, cookie values and body resolved. The input
     * {@code original} is never modified. Throws {@link UnresolvedVariableException}
     * listing every unknown name if any placeholder can't be resolved - in which
     * case the caller must not send anything.
     */
    public SendRequestDto resolveRequest(SendRequestDto original, Map<String, String> variables) {
        Set<String> unknown = new LinkedHashSet<>();

        String url = resolve(original.url(), variables, unknown);
        String body = resolve(original.body(), variables, unknown);

        List<HeaderDto> headers = original.headers() == null ? null : original.headers().stream()
                .map(h -> new HeaderDto(h.key(), resolve(h.value(), variables, unknown)))
                .toList();

        List<CookieDto> cookies = original.cookies() == null ? null : original.cookies().stream()
                .map(c -> new CookieDto(c.key(), resolve(c.value(), variables, unknown)))
                .toList();

        if (!unknown.isEmpty()) {
            throw new UnresolvedVariableException(unknown);
        }

        return new SendRequestDto(original.method(), url, headers, cookies, body,
                original.environmentId(), original.curlOptions());
    }
}
