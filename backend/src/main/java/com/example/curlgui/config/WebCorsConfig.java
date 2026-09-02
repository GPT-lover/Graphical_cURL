package com.example.curlgui.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) rules for local development.
 *
 * <p>The React dev server runs on {@code http://localhost:5173} (Vite's default)
 * while this backend runs on {@code http://localhost:8080}. Because the ports
 * differ, the browser treats frontend -> backend calls as "cross-origin" and
 * blocks them unless the backend explicitly opts in. Here we allow the Vite dev
 * server origins for any {@code /api/**} path.
 *
 * <p>{@code @Configuration} marks this as a class Spring reads at startup to
 * configure the application. Implementing {@code WebMvcConfigurer} lets us tweak
 * Spring MVC (just the CORS rules here) while keeping all its other defaults.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                // No cookies / auth are used in this app, so credentials stay off.
                .allowCredentials(false);
    }
}
