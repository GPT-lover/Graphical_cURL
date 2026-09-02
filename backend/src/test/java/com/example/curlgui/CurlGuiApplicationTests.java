package com.example.curlgui;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: {@code @SpringBootTest} boots the entire Spring application
 * context. If any bean is misconfigured (bad CORS class, broken datasource,
 * missing dependency, ...) this test fails. If it passes, "the app starts".
 */
@SpringBootTest
class CurlGuiApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty - success == the context loaded without error.
    }
}
