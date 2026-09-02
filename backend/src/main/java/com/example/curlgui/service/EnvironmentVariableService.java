package com.example.curlgui.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.curlgui.dto.EnvironmentVariableDto;
import com.example.curlgui.dto.SaveVariableDto;
import com.example.curlgui.model.EnvironmentVariable;
import com.example.curlgui.repository.EnvironmentRepository;
import com.example.curlgui.repository.EnvironmentVariableRepository;

/**
 * CRUD for the variables inside an environment, plus {@link #variablesFor} which
 * the request executor uses at send time.
 *
 * <p>Rules: the environment must exist (404); the key must be non-blank and a
 * valid name ({@code [A-Za-z0-9_.-]+}, so no braces/spaces/pipes) (400); keys
 * are unique within an environment, case-sensitively (409); empty values are
 * allowed. Values are never logged.
 */
@Service
public class EnvironmentVariableService {

    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_.\\-]+");

    private final EnvironmentVariableRepository variableRepository;
    private final EnvironmentRepository environmentRepository;

    public EnvironmentVariableService(EnvironmentVariableRepository variableRepository,
                                     EnvironmentRepository environmentRepository) {
        this.variableRepository = variableRepository;
        this.environmentRepository = environmentRepository;
    }

    @Transactional
    public EnvironmentVariableDto create(long environmentId, SaveVariableDto dto) {
        requireEnvironment(environmentId);
        String key = requireKey(dto);
        String value = dto.value() == null ? "" : dto.value();

        variableRepository.findByEnvironmentIdAndVarKey(environmentId, key).ifPresent(x -> {
            throw new ConflictException("Variable \"" + key + "\" already exists in this environment.");
        });

        EnvironmentVariable entity = new EnvironmentVariable();
        entity.setEnvironmentId(environmentId);
        entity.setVarKey(key);
        entity.setVarValue(value);
        entity = variableRepository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public EnvironmentVariableDto update(long environmentId, long variableId, SaveVariableDto dto) {
        requireEnvironment(environmentId);
        EnvironmentVariable entity = find(environmentId, variableId);
        String key = requireKey(dto);
        String value = dto.value() == null ? "" : dto.value();

        variableRepository.findByEnvironmentIdAndVarKey(environmentId, key).ifPresent(other -> {
            if (!other.getId().equals(entity.getId())) {
                throw new ConflictException(
                        "Variable \"" + key + "\" already exists in this environment.");
            }
        });

        entity.setVarKey(key);
        entity.setVarValue(value);
        return toDto(variableRepository.save(entity));
    }

    @Transactional
    public void delete(long environmentId, long variableId) {
        requireEnvironment(environmentId);
        variableRepository.delete(find(environmentId, variableId));
    }

    /**
     * The variable map to substitute for a send. Empty map if no environment is
     * given; {@link NotFoundException} if an unknown id is given (a stale
     * selection on the frontend).
     */
    @Transactional(readOnly = true)
    public Map<String, String> variablesFor(Long environmentId) {
        if (environmentId == null) {
            return Map.of();
        }
        if (!environmentRepository.existsById(environmentId)) {
            throw new NotFoundException("Environment " + environmentId + " not found.");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (EnvironmentVariable v : variableRepository.findByEnvironmentIdOrderByIdAsc(environmentId)) {
            map.put(v.getVarKey(), v.getVarValue() == null ? "" : v.getVarValue());
        }
        return map;
    }

    // ------------------------------------------------------------------

    private void requireEnvironment(long environmentId) {
        if (!environmentRepository.existsById(environmentId)) {
            throw new NotFoundException("Environment " + environmentId + " not found.");
        }
    }

    private EnvironmentVariable find(long environmentId, long variableId) {
        EnvironmentVariable v = variableRepository.findById(variableId)
                .orElseThrow(() -> new NotFoundException("Variable " + variableId + " not found."));
        if (!v.getEnvironmentId().equals(environmentId)) {
            throw new NotFoundException(
                    "Variable " + variableId + " is not in environment " + environmentId + ".");
        }
        return v;
    }

    private String requireKey(SaveVariableDto dto) {
        if (dto == null) {
            throw new InvalidRequestException("Variable data is missing.");
        }
        String key = dto.key() == null ? "" : dto.key().trim();
        if (key.isEmpty()) {
            throw new InvalidRequestException("Variable key must not be blank.");
        }
        if (!VALID_KEY.matcher(key).matches()) {
            throw new InvalidRequestException(
                    "Variable key may only contain letters, digits, '_', '.' and '-'.");
        }
        return key;
    }

    private EnvironmentVariableDto toDto(EnvironmentVariable v) {
        return new EnvironmentVariableDto(v.getId(), v.getVarKey(),
                v.getVarValue() == null ? "" : v.getVarValue());
    }
}
