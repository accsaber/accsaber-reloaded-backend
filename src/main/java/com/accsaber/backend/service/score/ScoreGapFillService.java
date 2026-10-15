package com.accsaber.backend.service.score;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.entity.map.LeaderboardPlatform;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.score.PlatformScores.ImportTarget;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreGapFillService {

    private static final int MAX_CONCURRENT_DIFFICULTIES = 3;

    private final ScoreImporter importer;
    private final ScoreImportRecalculator recalculator;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyComplexityService mapComplexityService;
    private final ModifierCacheService modifierCacheService;
    private final ScoreRankingService scoreRankingService;

    @Value("${accsaber.backfill.gap-fill-page-delay-ms:125}")
    private long gapFillPageDelayMs;

    @Async("taskExecutor")
    public void startupGapFillAllRankedDifficulties(Instant since) {
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED);
        log.info("Starting parallel gap-fill for {} ranked difficulties since {}", ranked.size(), since);

        long start = System.currentTimeMillis();
        Map<MapDifficulty, Set<Long>> imported = new ConcurrentHashMap<>();

        ImportConcurrency.forEachBounded(ranked, MAX_CONCURRENT_DIFFICULTIES, difficulty -> {
            try {
                Set<Long> users = startupGapFillImport(difficulty, since);
                if (users != null) {
                    imported.put(difficulty, users);
                }
            } catch (Exception e) {
                log.error("Error gap-filling difficulty {}: {}", difficulty.getId(), e.getMessage());
            }
        });

        recalculator.recalculateAfterImport(imported, false);

        Set<Long> settledUsers = imported.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
        recalculator.recoverCampaignsForParticipants(settledUsers);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Parallel gap-fill complete for {} difficulties in {}s", ranked.size(), elapsed / 1000);
        log.info("Running post-gap-fill rank repair");
        scoreRankingService.reassignAllRanks();
    }

    private Set<Long> startupGapFillImport(MapDifficulty difficulty, Instant since) {
        Double complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping gap-fill", difficulty.getId());
            return null;
        }

        ImportTarget target = new ImportTarget(difficulty, complexity, modifierCacheService.getModifierCodeToId());
        Set<Long> affectedUserIds = new HashSet<>();

        if (difficulty.getBlLeaderboardId() != null) {
            affectedUserIds.addAll(importer.gapFillFrom(importer.beatLeader(), target, since, gapFillPageDelayMs));
        }

        if (importer.scoreSaberEnabled() && difficulty.getSsLeaderboardId() != null) {
            affectedUserIds.addAll(importer.gapFillFrom(importer.scoreSaber(), target, since, gapFillPageDelayMs));
        }

        return affectedUserIds;
    }

    public void gapFillDifficulty(MapDifficulty difficulty, Instant since, LeaderboardPlatform platform,
            boolean enrichOnly) {
        ImportTarget target = new ImportTarget(difficulty, null, modifierCacheService.getModifierCodeToId());
        long sinceEpoch = since.getEpochSecond();

        if (platform != LeaderboardPlatform.SCORESABER && difficulty.getBlLeaderboardId() != null) {
            for (BeatLeaderScoreResponse blScore : importer.beatLeader().recentScores(
                    difficulty.getBlLeaderboardId(), sinceEpoch)) {
                importer.importScore(importer.beatLeader(), blScore, target, enrichOnly);
            }
        }

        if (platform != LeaderboardPlatform.BEATLEADER && importer.scoreSaberEnabled()
                && difficulty.getSsLeaderboardId() != null) {
            ScoreSaberScores scoreSaber = importer.scoreSaber();
            importer.importPages(page -> scoreSaber.recentPage(difficulty, page),
                    score -> scoreSaber.cutoff(score, sinceEpoch),
                    batch -> batch.stream().map(score -> importer.importScore(scoreSaber, score, target, enrichOnly))
                            .toList(),
                    0);
        }
    }
}
