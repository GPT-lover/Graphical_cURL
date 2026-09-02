package com.example.curlgui.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the single {@link HttpClient} the app uses for every outgoing request.
 *
 * <p>Why a {@code @Bean}? A {@code @Configuration} class with {@code @Bean}
 * methods is how you hand Spring an object it should create once and share
 * (inject) wherever it's needed. {@code HttpClient} is thread-safe and meant to
 * be reused - creating one per request would waste its connection pool. Any
 * component that declares an {@code HttpClient} constructor parameter now gets
 * this instance.
 *
 * <p>Deliberate settings:
 * <ul>
 *   <li><b>connectTimeout = 10s</b> - how long to wait for the TCP connection to
 *       be established. Unreachable / filtered hosts fail here quickly instead of
 *       hanging.</li>
 *   <li><b>followRedirects = NEVER</b> - this is a debugging tool: if the target
 *       replies 301/302 the user wants to <em>see</em> that and the
 *       {@code Location} header, not have it silently followed. (This is also the
 *       JDK default.)</li>
 *   <li>TLS verification is left at the JDK default - we do <b>not</b> disable
 *       certificate checking.</li>
 * </ul>
 *
 * <p>The per-request read timeout (30s) is set on each {@code HttpRequest} in
 * {@code RequestService}, not here.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
