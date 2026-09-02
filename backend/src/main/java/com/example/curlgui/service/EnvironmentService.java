package com.example.curlgui.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.curlgui.dto.EnvironmentDto;
import com.example.curlgui.dto.EnvironmentSummaryDto;
import com.example.curlgui.dto.EnvironmentVariableDto;
import com.example.curlgui.model.Environment;
import com.example.curlgui.model.EnvironmentVariable;
import com.example.curlgui.repository.EnvironmentRepository;
import com.example.curlgui.repository.EnvironmentVariableRepository;

/**
 * CRUD for environments.
 *
 * <p>Rules: names must not be blank (400), names are unique case-insensitively
 * (409), deleting an environment deletes its variables, and the <b>only</b>
 * environment can't be deleted (409) - the user must always have one to send
 * with.
 */
@Service
public class EnvironmentService {

    public static final String DEFAULT_ENVIRONMENT_NAME = "Default";

    private final EnvironmentRepository environmentRepository;
    private final EnvironmentVariableRepository variableRepository;

    public EnvironmentService(EnvironmentRepository environmentRepository,
                              EnvironmentVariableRepository variableRepository) {
        this.environmentRepository = environmentRepository;
        this.variableRepository = variableRepository;
    }

    /** All environments, no variable values. */
    @Transactional(readOnly = true)
    public List<EnvironmentSummaryDto> listAll() {
        List<EnvironmentSummaryDto> out = new ArrayList<>();
        for (Environment e : environmentRepository.findAllByOrderByIdAsc()) {
            out.add(new EnvironmentSummaryDto(e.getId(), e.getName()));
        }
        return out;
    }

    /** One environment with its variables (values included - management UI only). */
    @Transactional(readOnly = true)
    public EnvironmentDto get(long id) {
        Environment e = find(id);
        List<EnvironmentVariableDto> vars = new ArrayList<>();
        for (EnvironmentVariable v : variableRepository.findByEnvironmentIdOrderByIdAsc(id)) {
            vars.add(new EnvironmentVariableDto(v.getId(), v.getVarKey(),
                    v.getVarValue() == null ? "" : v.getVarValue()));
        }
        return new EnvironmentDto(e.getId(), e.getName(), vars);
    }

    @Transactional
    public EnvironmentSummaryDto create(String rawName) {
        String name = requireName(rawName);
        environmentRepository.findByNameIgnoreCase(name).ifPresent(x -> {
            throw new ConflictException("An environment named \"" + name + "\" already exists.");
        });
        String now = Instant.now().toString();
        Environment e = new Environment();
        e.setName(name);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e = environmentRepository.save(e);
        return new EnvironmentSummaryDto(e.getId(), e.getName());
    }

    @Transactional
    public EnvironmentSummaryDto rename(long id, String rawName) {
        String name = requireName(rawName);
        Environment e = find(id);
        environmentRepository.findByNameIgnoreCase(name).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new ConflictException("An environment named \"" + name + "\" already exists.");
            }
        });
        e.setName(name);
        e.setUpdatedAt(Instant.now().toString());
        environmentRepository.save(e);
        return new EnvironmentSummaryDto(e.getId(), e.getName());
    }

    @Transactional
    public void delete(long id) {
        if (!environmentRepository.existsById(id)) {
            throw new NotFoundException("Environment " + id + " not found.");
        }
        if (environmentRepository.count() <= 1) {
            throw new ConflictException(
                    "Cannot delete the only environment - at least one must always exist.");
        }
        variableRepository.deleteByEnvironmentId(id);
        environmentRepository.deleteById(id);
    }

    /** Create the "Default" environment once, on startup, if it isn't there. */
    @Transactional
    public void ensureDefaultEnvironment() {
        if (environmentRepository.findByNameIgnoreCase(DEFAULT_ENVIRONMENT_NAME).isEmpty()) {
            create(DEFAULT_ENVIRONMENT_NAME);
        }
    }

    // ------------------------------------------------------------------

    private Environment find(long id) {
        return environmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Environment " + id + " not found."));
    }

    private String requireName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new InvalidRequestException("Environment name must not be blank.");
        }
        return name;
    }
}
