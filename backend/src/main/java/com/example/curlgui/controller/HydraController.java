package com.example.curlgui.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.curlgui.dto.HydraAttackConfigDto;
import com.example.curlgui.dto.HydraAttackStartedDto;
import com.example.curlgui.dto.HydraAttackStatusDto;
import com.example.curlgui.dto.HydraDetectResultDto;
import com.example.curlgui.dto.HydraSettingsDto;
import com.example.curlgui.dto.UpdateHydraSettingsDto;
import com.example.curlgui.service.HydraAttackService;
import com.example.curlgui.service.HydraSettingsService;

/**
 * Endpoints for the Hydra integration. Graphical cURL never bundles or
 * reimplements THC Hydra - this only configures and launches an
 * already-installed external executable, whether native ({@code LOCAL} mode)
 * or installed inside a WSL distribution ({@code WSL} mode) - see
 * {@link HydraAttackService}. The frontend never runs Hydra itself.
 *
 * <p>Poll model, same shape as {@code RunMultipleController}: {@code POST
 * /attacks} starts an async process and returns an {@code attackId}; the
 * frontend {@code GET}s status (passing how many output lines it already has
 * via {@code ?offset=}) and can {@code POST .../stop}.
 */
@RestController
@RequestMapping("/api/hydra")
public class HydraController {

    private final HydraSettingsService settingsService;
    private final HydraAttackService attackService;

    public HydraController(HydraSettingsService settingsService, HydraAttackService attackService) {
        this.settingsService = settingsService;
        this.attackService = attackService;
    }

    @GetMapping("/settings")
    public HydraSettingsDto getSettings() {
        return settingsService.get();
    }

    @PutMapping("/settings")
    public HydraSettingsDto updateSettings(@RequestBody(required = false) UpdateHydraSettingsDto body) {
        return settingsService.update(body);
    }

    /**
     * Tests the given settings (so the frontend can "Test" an edit before
     * saving it) - falls back to the currently saved settings if no body is
     * given. Runs Hydra's {@code -h} directly, or via
     * {@code wsl.exe -d <distro> -- <hydraPath> -h} for WSL mode.
     */
    @PostMapping("/detect")
    public HydraDetectResultDto detect(@RequestBody(required = false) HydraSettingsDto body) {
        HydraSettingsDto settings = (body == null) ? settingsService.get() : body;
        return attackService.detect(settings);
    }

    @PostMapping("/attacks")
    public HydraAttackStartedDto start(@RequestBody(required = false) HydraAttackConfigDto body) {
        return attackService.start(settingsService.get(), body);
    }

    @GetMapping("/attacks/{attackId}")
    public HydraAttackStatusDto status(@PathVariable String attackId,
                                       @RequestParam(defaultValue = "0") int offset) {
        return attackService.status(attackId, offset);
    }

    @PostMapping("/attacks/{attackId}/stop")
    public ResponseEntity<Void> stop(@PathVariable String attackId) {
        attackService.stop(attackId);
        return ResponseEntity.noContent().build();
    }
}
