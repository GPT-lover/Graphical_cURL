package com.example.curlgui.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A named set of variables ({@link EnvironmentVariable}) that can be substituted
 * into a request's URL, header values, cookie values and body before it is sent.
 *
 * <p>The link to variables is a plain {@code environmentId} column on
 * {@link EnvironmentVariable} (no JPA {@code @OneToMany}), same pattern as
 * collections/saved requests. Deleting an environment deletes its variables via
 * explicit logic in {@code EnvironmentService}.
 */
@Entity
@Table(name = "environments")
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** ISO-8601 instant strings, consistent with the other entities. */
    private String createdAt;
    private String updatedAt;

    public Environment() {
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
