package com.example.curlgui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * {@code @SpringBootApplication} is three annotations in one:
 * <ul>
 *   <li>{@code @Configuration} - this class may define beans</li>
 *   <li>{@code @EnableAutoConfiguration} - Spring Boot configures the web server,
 *       JSON support, JPA, etc. based on what it finds on the classpath</li>
 *   <li>{@code @ComponentScan} - Spring scans this package and its sub-packages
 *       for {@code @RestController}, {@code @Service}, {@code @Repository} ...</li>
 * </ul>
 */
@SpringBootApplication
public class CurlGuiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CurlGuiApplication.class, args);
    }
}
