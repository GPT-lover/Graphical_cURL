package com.example.curlgui.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.curlgui.model.Environment;

/** Spring Data repository for {@link Environment}. */
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    /** All environments in creation order (so "Default" stays first). */
    List<Environment> findAllByOrderByIdAsc();

    /** Keeps the default environment unique and rejects duplicate names. */
    Optional<Environment> findByNameIgnoreCase(String name);
}
