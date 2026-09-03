// ---------------------------------------------------------------------------
// GroovesTransportTest — transport-isolation check (diagnostic only)
// ---------------------------------------------------------------------------
//
// PURPOSE
//   Determine whether Java's java.net.http.HttpClient receives Vercel's
//   "challenge" mitigation (HTTP 429 + x-vercel-mitigated: challenge)
//   INDEPENDENTLY of the curl-gui application, using the SAME client
//   configuration the app uses.
//
//   It is not part of the application build. It sends exactly ONE request and
//   prints what came back. It does not retry, does not follow the challenge,
//   does not touch TLS settings, and makes no attempt to bypass or evade any
//   security control.
//
// WHAT IT REPRODUCES FROM THE APP (do not "fix" these — the point is fidelity)
//   backend/.../config/HttpClientConfig.java:
//       HttpClient.newBuilder()
//           .connectTimeout(Duration.ofSeconds(10))
//           .followRedirects(HttpClient.Redirect.NEVER)
//           .build();                       // no .version(...) => default HTTP/2
//   backend/.../service/RequestService.java:
//       per-request .timeout(Duration.ofSeconds(30))
//       body via BodyPublishers.ofString(body, UTF_8)
//       headers copied in list order, then a SINGLE "Cookie" header appended last
//       BodyHandlers.ofByteArray()
//
// RUN (Java 17+; the project uses 21/25):
//   java manual-tests/GroovesTransportTest.java
//
//   See exactly what Java puts on the wire (header names/order/casing):
//   java -Djdk.httpclient.HttpClient.log=requests,headers manual-tests/GroovesTransportTest.java
//
// PROVIDING THE COOKIE / TOKEN  (never hard-coded; pick ONE)
//   PowerShell:
//     $env:GROOVES_COOKIE = 'sb-...-auth-token=base64-eyJ...'   # full Cookie header value
//       - or -
//     $env:GROOVES_AUTH_TOKEN = 'base64-eyJ...'                 # just the token value
//       - or -
//     $env:GROOVES_COOKIE_FILE = 'C:\path\cookie.txt'           # first non-blank line
//       - or -
//     run it and paste the value at the prompt (input is hidden)
//       - or -
//     provide nothing => the request is sent with NO Cookie header
//       (still a valid transport test; expect 401/redirect/challenge)
//
// OPTIONAL OVERRIDES (env vars)
//   GROOVES_URL    default https://grooves-web.vercel.app/track/1442954500
//   GROOVES_BODY   default ["1442954500",false]
//     -> To do a strict apples-to-apples vs the working curl, set these to the
//        curl's own values: URL .../album/1442954489 and BODY ["1442954489",true]
//   GROOVES_PROXY  host:port  -> route through mitmproxy/Fiddler to capture the
//     wire. NOTE: the app itself sets no proxy selector, so this deviates from
//     the app config; use it only for packet capture, then compare with a plain
//     run.
//
// A useful side check: set GROOVES_URL to a TLS/HTTP2 fingerprint echo service
// you trust and compare its JSON against `curl -v` for the same command — the
// JA3/JA4 and HTTP/2 fingerprints will differ between this JDK client and curl.
// ---------------------------------------------------------------------------

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class GroovesTransportTest {

    static final String DEFAULT_URL  = "https://grooves-web.vercel.app/track/1442954500";
    static final String DEFAULT_BODY = "[\"1442954500\",false]";

    // The cookie NAME is from your "Copy as cURL"; only the VALUE is sensitive
    // and is never taken from source here.
    static final String COOKIE_NAME  = "base64-eyJhY2Nlc3NfdG9rZW4iOiJleUpoYkdjaU9pSklVekkxTmlJc0ltdHBaQ0k2SWtJcksweE1kMVEzYUd0VmRuaDJZakFpTENKMGVYQWlPaUpLVjFRaWZRLmV5SnBjM01pT2lKb2RIUndjem92TDNCc1puUndkbnB1WTNGNmJtUm1jMlZpYjJOd0xuTjFjR0ZpWVhObExtTnZMMkYxZEdndmRqRWlMQ0p6ZFdJaU9pSmhPR0poWVdNMU1TMW1aamt4TFRRd09HSXRZalkxTUMxaU9EZGlNMk14TmpFMk9EWWlMQ0poZFdRaU9pSmhkWFJvWlc1MGFXTmhkR1ZrSWl3aVpYaHdJam94TnpnNE5EVXdNVFU1TENKcFlYUWlPakUzT0RnME5EWTFOVGtzSW1WdFlXbHNJam9pYzNSdlkydHdhV3hsTURNd01rQm5iV0ZwYkM1amIyMGlMQ0p3YUc5dVpTSTZJaUlzSW1Gd2NGOXRaWFJoWkdGMFlTSTZleUp3Y205MmFXUmxjaUk2SW1WdFlXbHNJaXdpY0hKdmRtbGtaWEp6SWpwYkltVnRZV2xzSWwxOUxDSjFjMlZ5WDIxbGRHRmtZWFJoSWpwN0ltVnRZV2xzSWpvaWMzUnZZMnR3YVd4bE1ETXdNa0JuYldGcGJDNWpiMjBpTENKbGJXRnBiRjkyWlhKcFptbGxaQ0k2ZEhKMVpTd2ljR2h2Ym1WZmRtVnlhV1pwWldRaU9tWmhiSE5sTENKemRXSWlPaUpoT0dKaFlXTTFNUzFtWmpreExUUXdPR0l0WWpZMU1DMWlPRGRpTTJNeE5qRTJPRFlpTENKMWMyVnlibUZ0WlNJNkltaGhZMnRsY2lKOUxDSnliMnhsSWpvaVlYVjBhR1Z1ZEdsallYUmxaQ0lzSW1GaGJDSTZJbUZoYkRFaUxDSmhiWElpT2x0N0ltMWxkR2h2WkNJNkluQmhjM04zYjNKa0lpd2lkR2x0WlhOMFlXMXdJam94TnpnNE5ETXlNVGc0ZlYwc0luTmxjM05wYjI1ZmFXUWlPaUprWTJSbFpXSTNOUzAwT1RBMUxUUTFPR010T0RFNE1DMWpOamhrWkRNelpERmtPVFVpTENKcGMxOWhibTl1ZVcxdmRYTWlPbVpoYkhObGZRLnVNbmQ1SE1zUEdJUndVWklyQThSaHZ1Y2xUbmo0WlN1aldZdElaTGhqdmsiLCJ0b2tlbl90eXBlIjoiYmVhcmVyIiwiZXhwaXJlc19pbiI6MzYwMCwiZXhwaXJlc19hdCI6MTc4ODQ1MDE1OSwicmVmcmVzaF90b2tlbiI6InVoN2NpYTdlM3F6ZSIsInVzZXIiOnsiaWQiOiJhOGJhYWM1MS1mZjkxLTQwOGItYjY1MC1iODdiM2MxNjE2ODYiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwicm9sZSI6ImF1dGhlbnRpY2F0ZWQiLCJlbWFpbCI6InN0b2NrcGlsZTAzMDJAZ21haWwuY29tIiwiZW1haWxfY29uZmlybWVkX2F0IjoiMjAyNi0wOC0yMVQyMDoyNzoyOS4wODg5OTNaIiwicGhvbmUiOiIiLCJjb25maXJtYXRpb25fc2VudF9hdCI6IjIwMjYtMDgtMjFUMjA6Mjc6MTEuMzcyOTkzWiIsImNvbmZpcm1lZF9hdCI6IjIwMjYtMDgtMjFUMjA6Mjc6MjkuMDg4OTkzWiIsImxhc3Rfc2lnbl9pbl9hdCI6IjIwMjYtMDktMDNUMTA6NDM6MDguMTk4MTI5WiIsImFwcF9tZXRhZGF0YSI6eyJwcm92aWRlciI6ImVtYWlsIiwicHJvdmlkZXJzIjpbImVtYWlsIl19LCJ1c2VyX21ldGFkYXRhIjp7ImVtYWlsIjoic3RvY2twaWxlMDMwMkBnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwicGhvbmVfdmVyaWZpZWQiOmZhbHNlLCJzdWIiOiJhOGJhYWM1MS1mZjkxLTQwOGItYjY1MC1iODdiM2MxNjE2ODYiLCJ1c2VybmFtZSI6ImhhY2tlciJ9LCJpZGVudGl0aWVzIjpbeyJpZGVudGl0eV9pZCI6IjYzYTYxOGViLTE3M2ItNGE2YS1iOGUwLTE2NzI4OTg4NTU2NSIsImlkIjoiYThiYWFjNTEtZmY5MS00MDhiLWI2NTAtYjg3YjNjMTYxNjg2IiwidXNlcl9pZCI6ImE4YmFhYzUxLWZmOTEtNDA4Yi1iNjUwLWI4N2IzYzE2MTY4NiIsImlkZW50aXR5X2RhdGEiOnsiZW1haWwiOiJzdG9ja3BpbGUwMzAyQGdtYWlsLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJwaG9uZV92ZXJpZmllZCI6ZmFsc2UsInN1YiI6ImE4YmFhYzUxLWZmOTEtNDA4Yi1iNjUwLWI4N2IzYzE2MTY4NiIsInVzZXJuYW1lIjoiaGFja2VyIn0sInByb3ZpZGVyIjoiZW1haWwiLCJsYXN0X3NpZ25faW5fYXQiOiIyMDI2LTA4LTIxVDIwOjI3OjExLjM2OTMwM1oiLCJjcmVhdGVkX2F0IjoiMjAyNi0wOC0yMVQyMDoyNzoxMS4zNjkzNDhaIiwidXBkYXRlZF9hdCI6IjIwMjYtMDgtMjFUMjA6Mjc6MTEuMzY5MzQ4WiIsImVtYWlsIjoic3RvY2twaWxlMDMwMkBnbWFpbC5jb20ifV0sImNyZWF0ZWRfYXQiOiIyMDI2LTA4LTIxVDIwOjI3OjExLjM2NTk2WiIsInVwZGF0ZWRfYXQiOiIyMDI2LTA5LTAzVDE0OjQyOjM5Ljc3MzA1WiIsImlzX2Fub255bW91cyI6ZmFsc2V9fQ";

    static final int BODY_PREVIEW_BYTES = 2000;

    public static void main(String[] args) throws Exception {
        String url  = env("GROOVES_URL", DEFAULT_URL);
        String body = env("GROOVES_BODY", DEFAULT_BODY);
        String cookieHeaderValue = resolveCookie();

        // --- same HttpClient configuration as HttpClientConfig.java -----------
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER);

        String proxy = System.getenv("GROOVES_PROXY");
        if (proxy != null && proxy.contains(":")) {
            String[] hp = proxy.split(":", 2);
            clientBuilder.proxy(ProxySelector.of(
                    new InetSocketAddress(hp[0].trim(), Integer.parseInt(hp[1].trim()))));
            System.out.println("(routing through proxy " + proxy + " — deviates from app config)");
        }
        HttpClient client = clientBuilder.build();

        // --- headers, in the order the "Copy as cURL" lists them -------------
        // (RequestService copies the -H rows in order, then appends ONE Cookie
        //  header last — reproduced below.)
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("accept", "text/x-component");
        headers.put("accept-language", "en-US,en;q=0.9");
        headers.put("cache-control", "no-cache");
        headers.put("content-type", "text/plain;charset=UTF-8");
        headers.put("next-action", "607c312e3e33ab8904d69da2c81ee03a31fcf7aab2");
        headers.put("next-router-state-tree",
                "%5B%22%22%2C%7B%22children%22%3A%5B%22album%22%2C%7B%22children%22%3A%5B%5B%22id%22%2C%22"
                + "1442954489%22%2C%22d%22%2Cnull%5D%2C%7B%22children%22%3A%5B%22__PAGE__%22%2C%7B%7D%2Cnull"
                + "%2Cnull%2C4096%5D%7D%2Cnull%2Cnull%2C4096%5D%7D%2Cnull%2Cnull%2C4096%5D%7D%2Cnull%2Cnull%2C4112%5D");
        headers.put("origin", "https://grooves-web.vercel.app");
        headers.put("pragma", "no-cache");
        headers.put("priority", "u=1, i");
        headers.put("referer", "https://grooves-web.vercel.app/album/1442954489");
        headers.put("sec-ch-ua", "\"Not=A?Brand\";v=\"99\", \"Google Chrome\";v=\"151\", \"Chromium\";v=\"151\"");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"Windows\"");
        headers.put("sec-fetch-dest", "empty");
        headers.put("sec-fetch-mode", "cors");
        headers.put("sec-fetch-site", "same-origin");
        headers.put("user-agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/151.0.0.0 Safari/537.36");

        // --- build the request the same way RequestService does -------------
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .method("POST", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        for (Map.Entry<String, String> h : headers.entrySet()) {
            try {
                rb.header(h.getKey(), h.getValue());
            } catch (IllegalArgumentException ex) {
                // Matches the app: skip a header java.net.http refuses, keep going.
                System.out.println("skipped header \"" + h.getKey() + "\": " + ex.getMessage());
            }
        }

        boolean cookieSent = cookieHeaderValue != null && !cookieHeaderValue.isBlank();
        if (cookieSent) {
            rb.header("Cookie", cookieHeaderValue);
        }

        HttpRequest request = rb.build();

        // --- what we're about to do (no secrets) ----------------------------
        System.out.println("=== request ===");
        System.out.println("POST                : " + url);
        System.out.println("body                : " + body);
        System.out.println("client.connectTimeout: 10s   followRedirects: NEVER");
        System.out.println("client default ver. : " + client.version());
        System.out.println("header rows         : " + headers.size());
        System.out.println("Cookie header       : " + (cookieSent
                ? "sent (" + cookieHeaderValue.length() + " chars; value not shown)"
                : "NOT sent — no token supplied"));
        System.out.println();

        long t0 = System.nanoTime();
        HttpResponse<byte[]> resp;
        try {
            resp = client.send(request, BodyHandlers.ofByteArray());
        } catch (IOException | InterruptedException ex) {
            System.out.println("=== transport error (no HTTP response) ===");
            System.out.println(ex.getClass().getName() + ": " + ex.getMessage());
            return;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        // --- results -------------------------------------------------------
        System.out.println("=== response ===");
        System.out.println("HTTP status  : " + resp.statusCode());
        System.out.println("HTTP version : " + resp.version());
        System.out.println("elapsed      : " + ms + " ms");
        System.out.println();

        System.out.println("response headers:");
        resp.headers().map().forEach((name, values) ->
                System.out.println("  " + name + ": " + String.join(", ", values)));
        System.out.println();

        String mitigated = resp.headers().firstValue("x-vercel-mitigated").orElse(null);
        boolean challenged =
                resp.statusCode() == 429
                || mitigated != null
                || resp.headers().firstValue("x-vercel-challenge-token").isPresent();
        System.out.println("x-vercel-mitigated          : " + (mitigated == null ? "(absent)" : mitigated));
        System.out.println("Vercel challenge detected   : " + challenged);
        System.out.println();

        byte[] bytes = resp.body();
        int show = Math.min(bytes.length, BODY_PREVIEW_BYTES);
        System.out.println("response body (first " + show + " of " + bytes.length + " bytes):");
        System.out.println("----------------------------------------------------------------");
        System.out.println(new String(bytes, 0, show, StandardCharsets.UTF_8));
        System.out.println("----------------------------------------------------------------");
        System.out.println();

        System.out.println("VERDICT: java.net.http.HttpClient "
                + (challenged
                   ? "IS challenged by Vercel here (429 / x-vercel-mitigated). "
                     + "Since your CLI curl with the same headers is not, the difference is the "
                     + "transport (TLS/HTTP-2 stack), not the header text."
                   : "was NOT challenged (status " + resp.statusCode() + "). "
                     + "Transport alone does not explain the app's 429 — re-check headers, "
                     + "cookie freshness, URL/body, or run again vs the exact working curl values."));
    }

    // ---------------------------------------------------------------------

    static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /**
     * Resolve the Cookie header value without ever hard-coding it. Priority:
     *   1. GROOVES_COOKIE       full Cookie header value, used verbatim
     *   2. GROOVES_AUTH_TOKEN   just the token; wrapped as "<name>=<token>"
     *   3. GROOVES_COOKIE_FILE  path; first non-blank line, treated as (1) or (2)
     *   4. interactive prompt   hidden input; treated as (1) if it contains '=', else (2)
     *   5. null                 -> request sent with no Cookie header
     */
    static String resolveCookie() throws IOException {
        String full = System.getenv("GROOVES_COOKIE");
        if (full != null && !full.isBlank()) {
            return full.trim();
        }
        String token = System.getenv("GROOVES_AUTH_TOKEN");
        if (token != null && !token.isBlank()) {
            return COOKIE_NAME + "=" + token.trim();
        }
        String file = System.getenv("GROOVES_COOKIE_FILE");
        if (file != null && !file.isBlank()) {
            String line = Files.readAllLines(Path.of(file.trim())).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse("");
            if (!line.isEmpty()) {
                return line.contains("=") ? line : COOKIE_NAME + "=" + line;
            }
        }
        String pasted = promptForValue();
        if (pasted != null && !pasted.isBlank()) {
            String s = pasted.trim();
            return s.contains("=") ? s : COOKIE_NAME + "=" + s;
        }
        return null;
    }

    /** Hidden prompt when interactive; single stdin line when piped; null if neither. */
    static String promptForValue() throws IOException {
        Console console = System.console();
        if (console != null) {
            char[] chars = console.readPassword(
                    "Paste cookie value (token, or full 'name=value'), or press Enter to skip: ");
            return chars == null ? null : new String(chars);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return reader.ready() ? reader.readLine() : null;
    }
}
