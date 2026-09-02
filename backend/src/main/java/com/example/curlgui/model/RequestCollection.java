package com.example.curlgui.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A named group of {@link SavedRequest}s (a "collection" / folder in the sidebar).
 *
 * <p>Named {@code RequestCollection} rather than {@code Collection} to avoid
 * clashing with {@code java.util.Collection}. The table is {@code collections}.
 *
 * <p>The link to saved requests is modelled as a plain {@code collectionId}
 * column on {@link SavedRequest} (no JPA {@code @OneToMany}). Deleting a
 * collection deletes its requests via explicit logic in {@code CollectionService}
 * - simpler and no lazy-loading surprises with {@code open-in-view=false}.
 */
@Entity
@Table(name = "collections")
public class RequestCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** ISO-8601 instant strings, consistent with RequestHistory. */
    private String createdAt;
    private String updatedAt;

    public RequestCollection() {
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
