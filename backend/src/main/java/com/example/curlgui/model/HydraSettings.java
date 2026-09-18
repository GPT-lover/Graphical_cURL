package com.example.curlgui.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The settings the Hydra integration needs. Always one row (id fixed at 1) -
 * there is nothing per-environment or per-request about it.
 *
 * <p>Two execution modes are supported (see {@code HydraExecutionMode}):
 * <ul>
 *   <li>{@code LOCAL} - run {@link #executablePath} directly (a native Windows
 *       {@code hydra.exe}, or a native binary on Linux/macOS).</li>
 *   <li>{@code WSL} - the backend runs on Windows but Hydra is installed
 *       inside a WSL distribution (e.g. Kali Linux); the backend invokes it via
 *       {@code wsl.exe -d <wslDistro> -- <wslHydraPath> ...} instead.</li>
 * </ul>
 */
@Entity
@Table(name = "hydra_settings")
public class HydraSettings {

    @Id
    private Long id;

    @Column(name = "execution_mode", length = 16)
    private String executionMode;

    @Column(name = "executable_path", length = 4096)
    private String executablePath;

    @Column(name = "wsl_distro", length = 256)
    private String wslDistro;

    @Column(name = "wsl_hydra_path", length = 4096)
    private String wslHydraPath;

    public HydraSettings() {
        // for JPA / direct construction
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public String getExecutablePath() {
        return executablePath;
    }

    public void setExecutablePath(String executablePath) {
        this.executablePath = executablePath;
    }

    public String getWslDistro() {
        return wslDistro;
    }

    public void setWslDistro(String wslDistro) {
        this.wslDistro = wslDistro;
    }

    public String getWslHydraPath() {
        return wslHydraPath;
    }

    public void setWslHydraPath(String wslHydraPath) {
        this.wslHydraPath = wslHydraPath;
    }
}
