package com.example.curlgui.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.example.curlgui.model.EnvironmentVariable;

/** Spring Data repository for {@link EnvironmentVariable}. */
public interface EnvironmentVariableRepository extends JpaRepository<EnvironmentVariable, Long> {

    List<EnvironmentVariable> findByEnvironmentIdOrderByIdAsc(Long environmentId);

    /** For the "duplicate key in the same environment" check (case-sensitive names). */
    Optional<EnvironmentVariable> findByEnvironmentIdAndVarKey(Long environmentId, String varKey);

    /** Delete every variable of an environment (called when the environment is deleted). */
    @Transactional
    void deleteByEnvironmentId(Long environmentId);
}
