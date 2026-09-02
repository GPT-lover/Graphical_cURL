package com.example.curlgui.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) rules.
 *
 * <p><b>Browser development.</b> The React dev server runs on
 * {@code http://localhost:5173} (Vite) while this backend runs on
 * {@code http://localhost:8080}. Different ports = "cross-origin", so the browser
 * blocks frontend -> backend calls unless the backend opts in. The defaults
 * below allow exactly the Vite dev-server origins for any {@code /api/**} path,
 * which is what plain {@code npm run dev} needs.
 *
 * <p><b>Electron desktop app (Phase 12).</b> When the packaged app loads the
 * built frontend from a {@code file://} path, the browser sends
 * {@code Origin: null}. The literal string {@code "null"} in the allow-list makes
 * that work. This is acceptable here because the server binds to loopback only,
 * uses no cookies or auth ({@code allowCredentials(false)}), and the app is an
 * explicitly local, single-user tool. (When Electron instead points a window at
 * {@code http://127.0.0.1:<port>/} the request is same-origin and CORS is not
 * consulted at all.)
 *
 * <p>The list can be overridden without a rebuild via the
 * {@code app.cors.allowed-origins} property (comma-separated), e.g. passed by
 * Electron as {@code --app.cors.allowed-origins=...}.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebCorsConfig(
            @Value("${app.cors.allowed-origins:"
                    + "http://localhost:5173,http://127.0.0.1:5173,null}")
            String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                // No cookies / auth are used in this app, so credentials stay off.
                .allowCredentials(false);
    }
}
