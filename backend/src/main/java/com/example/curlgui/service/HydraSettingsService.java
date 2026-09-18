package com.example.curlgui.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.curlgui.dto.HydraSettingsDto;
import com.example.curlgui.dto.UpdateHydraSettingsDto;
import com.example.curlgui.model.HydraSettings;
import com.example.curlgui.repository.HydraSettingsRepository;

/**
 * Holds the settings the Hydra integration needs: whether to run a local
 * executable or one installed inside WSL, and the path(s) for each mode. A
 * single row (id=1), created lazily with defaults on first read so the
 * frontend always has something to GET.
 */
@Service
public class HydraSettingsService {

    private static final long SETTINGS_ID = 1L;
    static final String DEFAULT_WSL_DISTRO = "kali-linux";
    static final String DEFAULT_WSL_HYDRA_PATH = "/usr/bin/hydra";

    private final HydraSettingsRepository repository;

    public HydraSettingsService(HydraSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public HydraSettingsDto get() {
        return toDto(find());
    }

    @Transactional
    public HydraSettingsDto update(UpdateHydraSettingsDto dto) {
        HydraExecutionMode mode = HydraExecutionMode.parse(dto == null ? null : dto.executionMode());
        HydraSettings entity = find();
        entity.setExecutionMode(mode.name());
        entity.setExecutablePath(trimOr(dto == null ? null : dto.executablePath(), ""));
        entity.setWslDistro(trimOrDefault(dto == null ? null : dto.wslDistro(), DEFAULT_WSL_DISTRO));
        entity.setWslHydraPath(trimOrDefault(dto == null ? null : dto.wslHydraPath(), DEFAULT_WSL_HYDRA_PATH));
        return toDto(repository.save(entity));
    }

    private HydraSettings find() {
        return repository.findById(SETTINGS_ID).orElseGet(() -> {
            HydraSettings entity = new HydraSettings();
            entity.setId(SETTINGS_ID);
            entity.setExecutionMode(HydraExecutionMode.LOCAL.name());
            entity.setExecutablePath("");
            entity.setWslDistro(DEFAULT_WSL_DISTRO);
            entity.setWslHydraPath(DEFAULT_WSL_HYDRA_PATH);
            return repository.save(entity);
        });
    }

    private static String trimOr(String raw, String fallback) {
        return raw == null ? fallback : raw.trim();
    }

    /** Like {@link #trimOr} but also falls back when the trimmed value is empty - for fields with a sensible default. */
    private static String trimOrDefault(String raw, String fallback) {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private HydraSettingsDto toDto(HydraSettings entity) {
        return new HydraSettingsDto(
                entity.getExecutionMode() == null ? HydraExecutionMode.LOCAL.name() : entity.getExecutionMode(),
                entity.getExecutablePath() == null ? "" : entity.getExecutablePath(),
                entity.getWslDistro() == null ? DEFAULT_WSL_DISTRO : entity.getWslDistro(),
                entity.getWslHydraPath() == null ? DEFAULT_WSL_HYDRA_PATH : entity.getWslHydraPath());
    }
}
