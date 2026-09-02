package com.example.curlgui.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A request the user intentionally saved, belonging to one
 * {@link RequestCollection} (via {@code collectionId}).
 *
 * <p>Like {@code RequestHistory}, <b>sensitive data is never stored here</b>:
 * {@code SavedRequestService} strips credential headers before saving, and there
 * is no cookies column at all.
 */
@Entity
@Table(name = "saved_requests")
public class SavedRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User-chosen display name, e.g. "Rate Release". Required, never blank. */
    private String name;

    /** FK to {@link RequestCollection#getId()}. */
    private Long collectionId;

    private String method;

    @Column(length = 1_048_576)
    private String url;

    /** Sanitised headers as a JSON array string. */
    @Column(length = 1_048_576)
    private String headers;

    @Column(length = 1_048_576)
    private String body;

    private String createdAt;
    private String updatedAt;

    public SavedRequest() {
        // for JPA / direct construction
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(Long collectionId) {
        this.collectionId = collectionId;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
