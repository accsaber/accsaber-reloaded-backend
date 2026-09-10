package com.accsaber.backend.service.map;

import java.util.ArrayList;
import java.util.EnumMap;
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
import com.accsaber.backend.model.entity.map.ComplexityEstimateSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplexityEstimateService {

    private static final List<MapDifficultyStatus> ESTIMATED_STATUSES = List.of(MapDifficultyStatus.RANKED,
            MapDifficultyStatus.QUALIFIED, MapDifficultyStatus.QUEUE);
    private static final long PAUSE_AFTER_NETWORK_MS = 250;

    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyComplexityEstimateRepository estimateRepository;
    private final List<ComplexityRater> raters;
    private final ComplexityModelClient modelClient;
    private final ComplexityScenarioService scenarioService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("backfillExecutor")
    public CompletableFuture<Void> refreshAllAsync() {
        List<MapDifficulty> difficulties = new ArrayList<>();
        for (MapDifficultyStatus status : ESTIMATED_STATUSES) {
            difficulties.addAll(mapDifficultyRepository.findByStatusAndActiveTrueWithCategory(status));
        }
        String modelHash = modelClient.health().map(Health::getModelHash).orElse(null);
        log.info("Complexity estimate refresh starting for {} difficulties with {} raters, model {}",
                difficulties.size(), raters.size(), modelHash);
        int stored = 0;
        int repriced = 0;
        for (MapDifficulty difficulty : difficulties) {
            Outcome outcome = refresh(difficulty, modelHash);
            stored += outcome.stored();
            repriced += outcome.repriced();
            if (outcome.network()) {
                pause();
            }
        }
        scenarioService.evict();
        log.info("Complexity estimate refresh complete, {} estimates stored, {} of them repriced from stored inputs",
                stored, repriced);
        return CompletableFuture.completedFuture(null);
    }

    public record Outcome(int stored, int repriced, boolean network) {
    }

    @Transactional
    public Outcome refresh(MapDifficulty difficulty, String modelHash) {
        int stored = 0;
        int repriced = 0;
        boolean network = false;
        for (ComplexityRater rater : raters) {
            Optional<MapDifficultyComplexityEstimate> existing = estimateRepository
                    .findByMapDifficultyIdAndSource(difficulty.getId(), rater.source());
            Optional<ComplexityRater.Rating> rating = existing
                    .flatMap(e -> rater.reprice(difficulty, e.getInputs(), modelHash));
            if (rating.isPresent()) {
                repriced++;
            } else {
                network = true;
                rating = rater.rate(difficulty);
            }
            if (rating.isEmpty()) {
                continue;
            }
            MapDifficultyComplexityEstimate estimate = existing.orElseGet(() -> MapDifficultyComplexityEstimate.builder()
                    .mapDifficulty(difficulty)
                    .source(rater.source())
                    .build());
            estimate.setComplexity(rating.get().complexity());
            estimate.setVersion(rater.version());
            estimate.setInputs(objectMapper.valueToTree(rating.get().inputs()));
            estimateRepository.save(estimate);
            stored++;
        }
        return new Outcome(stored, repriced, network);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Map<ComplexityEstimateSource, MapDifficultyComplexityEstimate>> estimatesFor(
            List<UUID> difficultyIds) {
        Map<UUID, Map<ComplexityEstimateSource, MapDifficultyComplexityEstimate>> result = new HashMap<>();
        if (difficultyIds.isEmpty()) {
            return result;
        }
        for (MapDifficultyComplexityEstimate estimate : estimateRepository.findAllByDifficultyIds(difficultyIds)) {
            result.computeIfAbsent(estimate.getMapDifficulty().getId(), k -> new EnumMap<>(ComplexityEstimateSource.class))
                    .put(estimate.getSource(), estimate);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<MapDifficultyComplexityEstimate> estimates(ComplexityEstimateSource source) {
        return estimateRepository.findAllBySourceWithCategory(source);
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_AFTER_NETWORK_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
