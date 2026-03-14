package com.accsaber.backend.service.score;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.infra.MetricsService;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.player.PlayerImportService;
import com.accsaber.backend.util.PlatformScoreMapper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ScoreIngestionService {

    private final ScoreService scoreService;
    private final PlayerImportService playerImportService;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final ScoreRepository scoreRepository;
    private final ScoreImportService scoreImportService;
    private final ModifierCacheService modifierCacheService;
    private final PlatformProperties properties;
    private final ScheduledExecutorService ingestionScheduler;

    private final MetricsService metricsService;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingSsScores = new ConcurrentHashMap<>();
    private volatile Set<String> rankedBlIds = Set.of();
    private volatile Set<String> rankedSsIds = Set.of();

    public ScoreIngestionService(ScoreService scoreService,
            PlayerImportService playerImportService,
            MapDifficultyRepository mapDifficultyRepository,
            ScoreRepository scoreRepository,
            ScoreImportService scoreImportService,
            ModifierCacheService modifierCacheService,
            PlatformProperties properties,
            @Qualifier("ingestionScheduler") ScheduledExecutorService ingestionScheduler,
            MetricsService metricsService) {
        this.scoreService = scoreService;
        this.playerImportService = playerImportService;
        this.mapDifficultyRepository = mapDifficultyRepository;
        this.scoreRepository = scoreRepository;
        this.scoreImportService = scoreImportService;
        this.modifierCacheService = modifierCacheService;
        this.properties = properties;
        this.ingestionScheduler = ingestionScheduler;
        this.metricsService = metricsService;
    }

    @PostConstruct
    public void init() {
        refreshRankedLeaderboardIds();
    }

    public void handleBeatLeaderScore(BeatLeaderScoreResponse blScore) {
        if (!rankedBlIds.contains(blScore.getLeaderboardId())) {
            return;
        }

        try {
            if (blScore.getPlayer() == null || blScore.getPlayer().getId() == null) {
                log.warn("Received BL score with missing player data for leaderboard {}, skipping",
                        blScore.getLeaderboardId());
                return;
            }

            if (PlatformScoreMapper.hasBannedModifier(blScore.getModifiers())) {
                log.debug("Skipping BL score on {} - banned modifier(s): {}", blScore.getLeaderboardId(),
                        blScore.getModifiers());
                return;
            }

            Optional<MapDifficulty> diffOpt = mapDifficultyRepository
                    .findByBlLeaderboardId(blScore.getLeaderboardId());
            if (diffOpt.isEmpty())
                return;
            MapDifficulty difficulty = diffOpt.get();

            Long steamId = Long.parseLong(blScore.getPlayer().getId());
            String playKey = steamId + "_" + difficulty.getId();

            ScheduledFuture<?> pending = pendingSsScores.remove(playKey);
            if (pending != null) {
                pending.cancel(false);
                log.debug("Cancelled pending SS score for play key {}", playKey);
            }

            playerImportService.ensurePlayerExists(steamId);
            SubmitScoreRequest request = PlatformScoreMapper.fromBeatLeader(
                    blScore, difficulty.getId(), steamId, modifierCacheService.getModifierCodeToId());
            metricsService.getScoreProcessingTimer().record(() -> scoreService.submit(request));
            metricsService.getBlScoresIngested().increment();
            log.debug("Ingested BL score for player {} on difficulty {}", steamId, difficulty.getId());
        } catch (Exception e) {
            log.error("Error handling BL score: {}", e.getMessage());
        }
    }

    public void handleScoreSaberScore(ScoreSaberScoreResponse ssScore, Long steamId, String ssLeaderboardId) {
        if (!rankedSsIds.contains(ssLeaderboardId)) {
            return;
        }

        try {
            if (PlatformScoreMapper.hasBannedModifier(ssScore.getModifiers())) {
                log.debug("Skipping SS score on {} - banned modifier(s): {}", ssLeaderboardId, ssScore.getModifiers());
                return;
            }

            Optional<MapDifficulty> diffOpt = mapDifficultyRepository
                    .findBySsLeaderboardId(ssLeaderboardId);
            if (diffOpt.isEmpty())
                return;
            MapDifficulty difficulty = diffOpt.get();

            String playKey = steamId + "_" + difficulty.getId();

            if (scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(steamId, difficulty.getId())
                    .isPresent()) {
                return;
            }

            int delaySeconds = properties.getSsWaitForBlSeconds();
            ScheduledFuture<?> future = ingestionScheduler.schedule(() -> {
                try {
                    pendingSsScores.remove(playKey);
                    playerImportService.ensurePlayerExists(steamId);
                    SubmitScoreRequest request = PlatformScoreMapper.fromScoreSaber(
                            ssScore, difficulty.getId(), steamId, modifierCacheService.getModifierCodeToId());
                    metricsService.getScoreProcessingTimer().record(() -> scoreService.submit(request));
                    metricsService.getSsScoresIngested().increment();
                    log.debug("Ingested SS score for player {} on difficulty {}", steamId, difficulty.getId());
                } catch (Exception e) {
                    log.error("Error submitting delayed SS score: {}", e.getMessage());
                }
            }, delaySeconds, TimeUnit.SECONDS);

            ScheduledFuture<?> existing = pendingSsScores.put(playKey, future);
            if (existing != null) {
                existing.cancel(false);
            }
        } catch (Exception e) {
            log.error("Error handling SS score: {}", e.getMessage());
        }
    }

    public void refreshRankedLeaderboardIds() {
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrue(MapDifficultyStatus.RANKED);

        rankedBlIds = ranked.stream()
                .map(MapDifficulty::getBlLeaderboardId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        rankedSsIds = ranked.stream()
                .map(MapDifficulty::getSsLeaderboardId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        log.info("Refreshed ranked leaderboard IDs: {} BL, {} SS", rankedBlIds.size(), rankedSsIds.size());
    }

    public void gapFill(String platform, Instant disconnectedAt) {
        Duration gap = Duration.between(disconnectedAt, Instant.now());
        if (gap.getSeconds() > properties.getGapFillWindowSeconds()) {
            log.warn("Gap of {}s exceeds max {}s for {} - skipping gap fill",
                    gap.getSeconds(), properties.getGapFillWindowSeconds(), platform);
            return;
        }

        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrue(MapDifficultyStatus.RANKED);

        for (MapDifficulty difficulty : ranked) {
            boolean relevant = "beatleader".equals(platform)
                    ? difficulty.getBlLeaderboardId() != null
                    : difficulty.getSsLeaderboardId() != null;
            if (relevant) {
                try {
                    scoreImportService.gapFillDifficulty(difficulty, disconnectedAt);
                } catch (Exception e) {
                    log.error("Gap fill error for difficulty {}: {}", difficulty.getId(), e.getMessage());
                }
            }
        }
    }
}
