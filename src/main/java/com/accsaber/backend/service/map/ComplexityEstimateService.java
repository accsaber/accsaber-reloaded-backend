package com.accsaber.backend.service.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.client.ComplexityModelClient;
import com.accsaber.backend.client.ComplexityModelClient.Health;
import com.accsaber.backend.model.dto.projection.EstimateSummaryRow;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.map.NoteAccuracyComplexityRater.Rating;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplexityEstimateService {

    private static final List<MapDifficultyStatus> ESTIMATED_STATUSES = List.of(MapDifficultyStatus.RANKED,
            MapDifficultyStatus.QUALIFIED, MapDifficultyStatus.QUEUE);

    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyComplexityEstimateRepository estimateRepository;
    private final NoteAccuracyComplexityRater rater;
    private final ComplexityModelClient modelClient;
    private final ComplexityScenarioService scenarioService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record Outcome(boolean stored, boolean repriced) {
    }

    @Async("backfillExecutor")
    public CompletableFuture<Void> refreshAllAsync() {
        List<MapDifficulty> difficulties = new ArrayList<>();
        for (MapDifficultyStatus status : ESTIMATED_STATUSES) {
            difficulties.addAll(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(status));
        }
        String modelHash = modelClient.health().map(Health::getModelHash).orElse(null);
        log.info("Complexity estimate refresh starting for {} difficulties, script {}, model {}", difficulties.size(),
                rater.version(), modelHash);
        int stored = 0;
        int repriced = 0;
        for (MapDifficulty difficulty : difficulties) {
            Outcome outcome = refresh(difficulty, modelHash);
            stored += outcome.stored() ? 1 : 0;
            repriced += outcome.repriced() ? 1 : 0;
        }
        scenarioService.evict();
        scenarioService.rebuild();
        log.info("Complexity estimate refresh complete, {} estimates stored, {} of them worked out from stored inputs",
                stored, repriced);
        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    public Outcome refresh(MapDifficulty difficulty, String modelHash) {
        Optional<MapDifficultyComplexityEstimate> existing = estimateRepository.findByMapDifficultyId(difficulty.getId());
        Optional<Rating> repriced = existing.flatMap(e -> rater.reprice(difficulty, e.getInputs(), modelHash));
        Optional<Rating> rating = repriced.isPresent() ? repriced : rater.rate(difficulty);
        if (rating.isEmpty()) {
            return new Outcome(false, false);
        }
        MapDifficultyComplexityEstimate estimate = existing.orElseGet(() -> MapDifficultyComplexityEstimate.builder()
                .mapDifficulty(difficulty)
                .build());
        estimate.setComplexity(rating.get().complexity());
        estimate.setVersion(rater.version());
        estimate.setInputs(objectMapper.valueToTree(rating.get().inputs()));
        estimateRepository.save(estimate);
        return new Outcome(true, repriced.isPresent());
    }

    @Transactional(readOnly = true)
    public Map<UUID, MapDifficultyComplexityEstimate> estimatesFor(List<UUID> difficultyIds) {
        Map<UUID, MapDifficultyComplexityEstimate> result = new HashMap<>();
        if (difficultyIds.isEmpty()) {
            return result;
        }
        for (MapDifficultyComplexityEstimate estimate : estimateRepository.findAllByDifficultyIds(difficultyIds)) {
            result.put(estimate.getMapDifficulty().getId(), estimate);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<EstimateSummaryRow> summaryRowsFor(Collection<UUID> difficultyIds) {
        if (difficultyIds.isEmpty()) {
            return List.of();
        }
        return estimateRepository.findSummaryRows(difficultyIds);
    }

    @Transactional(readOnly = true)
    public List<MapDifficultyComplexityEstimate> estimates() {
        return estimateRepository.findAllWithCategory();
    }
}
