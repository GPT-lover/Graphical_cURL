package com.example.curlgui.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.curlgui.model.HydraSettings;

/** Spring Data repository for the single {@link HydraSettings} row. */
public interface HydraSettingsRepository extends JpaRepository<HydraSettings, Long> {
}
