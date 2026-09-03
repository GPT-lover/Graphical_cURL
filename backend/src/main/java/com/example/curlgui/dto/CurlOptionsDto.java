package com.example.curlgui.dto;

/**
 * Transport-level options taken verbatim from an imported cURL command that the
 * request editor does not model as fields, but that change the actual HTTP
 * request and so must be preserved when the request is executed through the real
 * {@code curl} executable.
 *
 * <p>All fields are optional. An instance where every field is {@code false} /
 * {@code null} means "no special options" and reproduces the previous default
 * behaviour (verify TLS, do not follow redirects, 10s connect / 30s total).
 *
 * <ul>
 *   <li>{@code compressed}            &rarr; {@code --compressed}</li>
 *   <li>{@code httpVersion}           &rarr; {@code --http1.0} / {@code --http1.1}
 *       / {@code --http2} / {@code --http2-prior-knowledge}
 *       (accepted values: {@code "1.0" "1.1" "2" "2-prior-knowledge"})</li>
 *   <li>{@code followRedirects}       &rarr; {@code --location} ({@code -L})</li>
 *   <li>{@code insecure}              &rarr; {@code --insecure} ({@code -k}) -
 *       honoured only when imported, and always with a loud warning</li>
 *   <li>{@code connectTimeoutSeconds} &rarr; {@code --connect-timeout}
 *       (default 10)</li>
 *   <li>{@code maxTimeSeconds}        &rarr; {@code --max-time} (default 30)</li>
 *   <li>{@code proxy}                 &rarr; {@code --proxy}</li>
 *   <li>{@code proxyUser}             &rarr; {@code --proxy-user} (sensitive -
 *       never logged)</li>
 * </ul>
 */
public record CurlOptionsDto(
        boolean compressed,
        String httpVersion,
        boolean followRedirects,
        boolean insecure,
        Integer connectTimeoutSeconds,
        Integer maxTimeSeconds,
        String proxy,
        String proxyUser
) {

    /** An options object with nothing set - the default behaviour. */
    public static CurlOptionsDto none() {
        return new CurlOptionsDto(false, null, false, false, null, null, null, null);
    }

    /** Never returns null: maps a missing options object to {@link #none()}. */
    public static CurlOptionsDto orNone(CurlOptionsDto value) {
        return value == null ? none() : value;
    }

    public boolean hasProxy() {
        return proxy != null && !proxy.isBlank();
    }

    public boolean hasProxyUser() {
        return proxyUser != null && !proxyUser.isBlank();
    }
}
