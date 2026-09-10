package com.accsaber.backend.scheduler;

import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.client.ComplexityModelClient;
import com.accsaber.backend.client.ComplexityModelClient.Health;
import com.accsaber.backend.model.dto.request.admin.RunJobRequest;
import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;
import com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository;
import com.accsaber.backend.service.admin.AdminJobService;
import com.accsaber.backend.service.admin.JobRegistry;
import com.accsaber.backend.service.admin.JobType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexityEstimateRefreshScheduler {

    private final ComplexityModelClient modelClient;
    private final MapDifficultyComplexityEstimateRepository estimateRepository;
    private final JobRegistry jobRegistry;
    private final AdminJobService adminJobService;

    @Scheduled(fixedDelayString = "${accsaber.complexity.model-check-interval-ms:300000}", initialDelay = 120_000)
    public void refreshWhenTheModelChanged() {
        try {
            check();
        } catch (Exception e) {
            log.error("Complexity model check failed: {}", e.getMessage(), e);
        }
    }

    void check() {
        if (jobRegistry.isRunning(JobType.REFRESH_COMPLEXITY_ESTIMATES)) {
            return;
        }
        Optional<Health> health = modelClient.health();
        if (health.isEmpty() || health.get().getModelHash() == null) {
            return;
        }
        String current = health.get().getModelHash();
        String stored = estimateRepository.findFirstBySourceOrderByUpdatedAtDesc(ComplexityEstimateSource.NEW_SCRIPT)
                .map(ComplexityEstimateRefreshScheduler::modelHashOf)
                .orElse(null);
        if (current.equals(stored)) {
            return;
        }
        log.info("Complexity model is {} ({}), estimates were made with {}, refreshing", health.get().getModel(),
                current, stored == null ? "nothing yet" : stored);
        RunJobRequest request = new RunJobRequest();
        request.setType(JobType.REFRESH_COMPLEXITY_ESTIMATES);
        adminJobService.run(request);
    }

    private static String modelHashOf(MapDifficultyComplexityEstimate estimate) {
        if (estimate.getInputs() == null || !estimate.getInputs().hasNonNull("modelHash")) {
            return null;
        }
        return estimate.getInputs().get("modelHash").asText();
    }
}
