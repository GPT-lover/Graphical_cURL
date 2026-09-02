package com.example.curlgui.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One {@code KEY = value} pair inside an {@link Environment}.
 *
 * <p>The Java fields are {@code varKey} / {@code varValue} (columns
 * {@code var_key} / {@code var_value}) to sidestep {@code key} / {@code value}
 * being reserved-ish words in SQL and in JPQL derived-query parsing. The JSON API
 * still uses {@code key} / {@code value}.
 *
 * <p>Values may be sensitive (tokens, passwords). They are stored in plain text
 * in SQLite for this phase but are never logged and never returned by unrelated
 * endpoints.
 */
@Entity
@Table(name = "environment_variables")
public class EnvironmentVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to {@link Environment#getId()}. */
    private Long environmentId;

    @Column(name = "var_key")
    private String varKey;

    @Column(name = "var_value", length = 1_048_576)
    private String varValue;

    public EnvironmentVariable() {
        // for JPA / direct construction
    }

    public Long getId() {
        return id;
    }

    public Long getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(Long environmentId) {
        this.environmentId = environmentId;
    }

    public String getVarKey() {
        return varKey;
    }

    public void setVarKey(String varKey) {
        this.varKey = varKey;
    }

    public String getVarValue() {
        return varValue;
    }

    public void setVarValue(String varValue) {
        this.varValue = varValue;
    }
}
