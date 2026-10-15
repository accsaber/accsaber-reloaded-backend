package com.accsaber.backend.service.score;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.map.LeaderboardPlatform;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.score.PlatformScores.ImportTarget;
import com.accsaber.backend.service.songsuggest.SongSuggestService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreImportService {

    private static final int MAX_CONCURRENT_DIFFICULTIES = 3;

    private final ScoreImporter importer;
    private final ScoreImportRecalculator recalculator;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyComplexityService mapComplexityService;
    private final ModifierCacheService modifierCacheService;
    private final ScoreRankingService scoreRankingService;
    private final SongSuggestService songSuggestService;

    public void backfillDifficulty(MapDifficulty difficulty) {
        Set<Long> affectedUserIds = importDifficulty(difficulty);
        if (affectedUserIds == null) {
            return;
        }
        recalculator.recalculateAfterImport(Map.of(difficulty, affectedUserIds), true);
    }

    private Set<Long> importDifficulty(MapDifficulty difficulty) {
        Double complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping backfill", difficulty.getId());
            return null;
        }

        ImportTarget target = new ImportTarget(difficulty, complexity, modifierCacheService.getModifierCodeToId());
        Set<Long> affectedUserIds = new HashSet<>();
        int blImported = 0;
        int ssImported = 0;

        if (difficulty.getBlLeaderboardId() != null) {
            Set<Long> blUsers = importer.backfillFrom(importer.beatLeader(), difficulty.getBlLeaderboardId(), target);
            blImported = blUsers.size();
            affectedUserIds.addAll(blUsers);
        }

        if (importer.scoreSaberEnabled() && difficulty.getSsLeaderboardId() != null) {
            Set<Long> ssUsers = importer.backfillFrom(importer.scoreSaber(), difficulty.getSsLeaderboardId(), target);
            ssImported = ssUsers.size();
            affectedUserIds.addAll(ssUsers);
        }

        log.info(
                "Backfill import done for difficulty {}: {} BL scores, {} SS scores ({} affected users)",
                difficulty.getId(), blImported, ssImported, affectedUserIds.size());

        return affectedUserIds;
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> backfillDifficultyAsync(UUID mapDifficultyId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithCategory(mapDifficultyId)
                .orElse(null);
        if (difficulty == null) {
            log.warn("Cannot backfill: difficulty {} not found or inactive", mapDifficultyId);
            return CompletableFuture.completedFuture(null);
        }
        backfillDifficulty(difficulty);
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutor")
    public void backfillLeaderboardAsync(UUID mapDifficultyId, LeaderboardPlatform platform, String leaderboardId) {
        if (platform == LeaderboardPlatform.SCORESABER && !importer.scoreSaberEnabled()) {
            log.info("ScoreSaber backfill disabled - skipping alias backfill for leaderboard {}", leaderboardId);
            return;
        }
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithCategory(mapDifficultyId)
                .orElse(null);
        if (difficulty == null) {
            log.warn("Cannot backfill alias leaderboard {}: difficulty {} not found or inactive",
                    leaderboardId, mapDifficultyId);
            return;
        }
        Double complexity = mapComplexityService.findActiveComplexity(mapDifficultyId).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping alias backfill", mapDifficultyId);
            return;
        }
        ImportTarget target = new ImportTarget(difficulty, complexity, modifierCacheService.getModifierCodeToId());
        Set<Long> affectedUserIds = platform == LeaderboardPlatform.BEATLEADER
                ? importer.backfillFrom(importer.beatLeader(), leaderboardId, target)
                : importer.backfillFrom(importer.scoreSaber(), leaderboardId, target);
        log.info("Alias backfill imported {} scores from {} leaderboard {} into difficulty {}",
                affectedUserIds.size(), platform, leaderboardId, mapDifficultyId);
        recalculator.recalculateAfterImport(Map.of(difficulty, affectedUserIds), false);
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> backfillDifficultiesAsync(List<UUID> mapDifficultyIds) {
        log.info("Starting parallel backfill for {} difficulties", mapDifficultyIds.size());
        long start = System.currentTimeMillis();
        Map<MapDifficulty, Set<Long>> imported = new ConcurrentHashMap<>();

        ImportConcurrency.forEachBounded(mapDifficultyIds, MAX_CONCURRENT_DIFFICULTIES, id -> {
            try {
                MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithCategory(id)
                        .orElse(null);
                if (difficulty == null) {
                    log.warn("Cannot backfill: difficulty {} not found or inactive", id);
                    return;
                }
                Set<Long> users = importDifficulty(difficulty);
                if (users != null) {
                    imported.put(difficulty, users);
                }
            } catch (Exception e) {
                log.error("Error backfilling difficulty {}: {}", id, e.getMessage());
            }
        });

        recalculator.recalculateAfterImport(imported, true);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Parallel backfill complete for {} difficulties in {}s", mapDifficultyIds.size(), elapsed / 1000);

        songSuggestService.regenerateAsync();
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> backfillAllRankedDifficulties() {
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED);
        log.info("Starting parallel backfill for {} ranked difficulties", ranked.size());
        long start = System.currentTimeMillis();
        Map<MapDifficulty, Set<Long>> imported = new ConcurrentHashMap<>();

        ImportConcurrency.forEachBounded(ranked, MAX_CONCURRENT_DIFFICULTIES, difficulty -> {
            try {
                Set<Long> users = importDifficulty(difficulty);
                if (users != null) {
                    imported.put(difficulty, users);
                }
            } catch (Exception e) {
                log.error("Error backfilling difficulty {}: {}", difficulty.getId(), e.getMessage());
            }
        });

        recalculator.recalculateAfterImport(imported, true);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Parallel backfill of all {} ranked difficulties complete in {}s", ranked.size(), elapsed / 1000);
        log.info("Running post-backfill rank repair");
        scoreRankingService.reassignAllRanks();
        return CompletableFuture.completedFuture(null);
    }
}
