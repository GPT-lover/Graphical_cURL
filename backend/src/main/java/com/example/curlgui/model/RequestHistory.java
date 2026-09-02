package com.example.curlgui.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One sent request plus its response summary, persisted to SQLite so the History
 * sidebar survives a backend restart.
 *
 * <p>{@code @Entity} maps this class to a database table. The table
 * ({@code request_history}) is created automatically on startup by
 * {@code spring.jpa.hibernate.ddl-auto=update}. Each field is a column; Spring
 * Boot's naming strategy turns {@code statusCode} into {@code status_code}, etc.
 *
 * <p><b>Sensitive data is never written here.</b> {@code RequestHistoryService}
 * strips Authorization / Cookie / token-like headers before saving, and cookies
 * are not persisted at all - this entity deliberately has no cookies field.
 *
 * <p>JPA requires a no-args constructor and mutable fields, so this is a plain
 * class with getters/setters rather than a {@code record}.
 */
@Entity
@Table(name = "request_history")
public class RequestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String method;

    // length is large so SQLite stores it as unbounded TEXT (SQLite ignores the
    // declared size); this keeps long URLs / bodies from being truncated.
    @Column(length = 1_048_576)
    private String url;

    /** Sanitised headers as a JSON array string: {@code [{"key":"Accept","value":"..."}]}. */
    @Column(length = 1_048_576)
    private String headers;

    /** Request body, stored verbatim. May be empty. */
    @Column(length = 1_048_576)
    private String body;

    private int statusCode;

    private long durationMs;

    /** ISO-8601 instant, e.g. {@code 2026-09-02T18:30:00.123Z}. Stored as TEXT. */
    private String createdAt;

    public RequestHistory() {
        // JPA needs a no-args constructor; the service also uses it directly.
    }

    public Long getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
