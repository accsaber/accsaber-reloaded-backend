package com.accsaber.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.map.ComplexityScenarioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexityScenarioWarmupScheduler {

    private final ComplexityScenarioService scenarioService;

    @Scheduled(fixedDelayString = "${accsaber.complexity.scenario-refresh-ms:600000}", initialDelay = 60_000)
    public void rebuildScenarioStates() {
        try {
            scenarioService.rebuild();
        } catch (Exception e) {
            log.error("Complexity scenario rebuild failed: {}", e.getMessage(), e);
        }
    }
}
