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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreStats;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.infra.MetricsService;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.player.DuplicateUserService;
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
    private final ScoreImportService scoreImportService;
    private final ModifierCacheService modifierCacheService;
    private final PlatformProperties properties;
    private final ScheduledExecutorService ingestionScheduler;

    private final MetricsService metricsService;
    private final DuplicateUserService duplicateUserService;
    private final ScoreSaberClient scoreSaberClient;
    private final CampaignScoreGate campaignScoreGate;

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
            MetricsService metricsService,
            DuplicateUserService duplicateUserService,
            ScoreSaberClient scoreSaberClient,
            CampaignScoreGate campaignScoreGate) {
        this.scoreService = scoreService;
        this.playerImportService = playerImportService;
        this.mapDifficultyRepository = mapDifficultyRepository;
        this.scoreImportService = scoreImportService;
        this.modifierCacheService = modifierCacheService;
        this.properties = properties;
        this.ingestionScheduler = ingestionScheduler;
        this.metricsService = metricsService;
        this.duplicateUserService = duplicateUserService;
        this.scoreSaberClient = scoreSaberClient;
        this.campaignScoreGate = campaignScoreGate;
    }

    @PostConstruct
    public void init() {
        refreshRankedLeaderboardIds();
    }

    public void handleBeatLeaderScore(BeatLeaderScoreResponse blScore) {
        boolean ranked = rankedBlIds.contains(blScore.getLeaderboardId());
        if (!ranked && !campaignScoreGate.matchesBlLeaderboard(blScore.getLeaderboardId())) {
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

            Long userId = duplicateUserService.resolvePrimaryUserId(
                    Long.parseLong(blScore.getPlayer().getId()));
            if (!ranked && !campaignScoreGate.isParticipant(userId)) {
                return;
            }

            Optional<MapDifficulty> diffOpt = mapDifficultyRepository
                    .findByBlLeaderboardId(blScore.getLeaderboardId());
            if (diffOpt.isEmpty())
                return;
            MapDifficulty difficulty = diffOpt.get();

            String playKey = userId + "_" + difficulty.getId();

            ScheduledFuture<?> pending = pendingSsScores.remove(playKey);
            if (pending != null) {
                pending.cancel(false);
                log.debug("Cancelled pending SS score for play key {}", playKey);
            }

            SubmitScoreRequest request = PlatformScoreMapper.fromBeatLeader(
                    blScore, difficulty.getId(), userId, modifierCacheService.getModifierCodeToId());
            if (ranked) {
                playerImportService.ensurePlayerExists(userId);
                metricsService.getScoreProcessingTimer().record(() -> scoreService.submit(request));
                metricsService.getBlScoresIngested().increment();
                log.info("Ingested BL score for player {} on difficulty {}", userId, difficulty.getId());
            } else {
                submitCampaignScoreQuietly(request, "BL", userId, difficulty.getId());
            }
        } catch (Exception e) {
            log.error("Error handling BL score: {}", e.getMessage());
        }
    }

    private void submitCampaignScoreQuietly(SubmitScoreRequest request, String platform, Long userId,
            java.util.UUID difficultyId) {
        try {
            scoreService.submitCampaignScore(request);
            log.info("Ingested {} campaign score for player {} on difficulty {}", platform, userId, difficultyId);
        } catch (ValidationException e) {
            log.debug("Dropped {} campaign score for player {} on difficulty {}: {}", platform, userId,
                    difficultyId, e.getMessage());
        }
    }

    public void handleScoreSaberScore(ScoreSaberScoreResponse ssScore, ScoreSaberScoreStats scoreStats,
            Long userId, String ssLeaderboardId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        boolean ranked = rankedSsIds.contains(ssLeaderboardId);
        if (!ranked && (!campaignScoreGate.matchesSsLeaderboard(ssLeaderboardId)
                || !campaignScoreGate.isParticipant(resolvedUserId))) {
            return;
        }

        try {
            if (PlatformScoreMapper.hasBannedModifier(ssScore.getMods())) {
                log.debug("Skipping SS score on {} - banned modifier(s): {}", ssLeaderboardId, ssScore.getMods());
                return;
            }

            Optional<MapDifficulty> diffOpt = mapDifficultyRepository
                    .findBySsLeaderboardId(ssLeaderboardId);
            if (diffOpt.isEmpty())
                return;
            MapDifficulty difficulty = diffOpt.get();

            String playKey = resolvedUserId + "_" + difficulty.getId();

            int delaySeconds = properties.getSsWaitForBlSeconds();
            ScheduledFuture<?> future = ingestionScheduler.schedule(() -> {
                try {
                    pendingSsScores.remove(playKey);
                    ScoreSaberScoreStats effectiveStats = resolveScoreStats(ssScore, scoreStats);
                    SubmitScoreRequest request = PlatformScoreMapper.fromScoreSaber(
                            ssScore, effectiveStats, difficulty.getId(), resolvedUserId,
                            modifierCacheService.getModifierCodeToId());
                    if (ranked) {
                        playerImportService.ensurePlayerExists(resolvedUserId);
                        metricsService.getScoreProcessingTimer().record(() -> scoreService.submit(request));
                        metricsService.getSsScoresIngested().increment();
                        log.info("Ingested SS score for player {} on difficulty {}", resolvedUserId,
                                difficulty.getId());
                    } else {
                        submitCampaignScoreQuietly(request, "SS", resolvedUserId, difficulty.getId());
                    }
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

    private ScoreSaberScoreStats resolveScoreStats(ScoreSaberScoreResponse ssScore, ScoreSaberScoreStats existing) {
        if (existing != null || ssScore.getId() == null) {
            return existing;
        }
        return scoreSaberClient.getScoreStats(ssScore.getId()).orElse(null);
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

    @Async("backfillExecutor")
    public void gapFill(String platform, Instant disconnectedAt) {
        Duration gap = Duration.between(disconnectedAt, Instant.now());
        if (gap.getSeconds() > properties.getGapFillWindowSeconds()) {
            log.warn("Gap of {}s exceeds max {}s for {} - skipping gap fill",
                    gap.getSeconds(), properties.getGapFillWindowSeconds(), platform);
            return;
        }

        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrue(MapDifficultyStatus.RANKED);
        log.info("Starting {} gap fill across {} ranked difficulties", platform, ranked.size());

        int throttleMs = "scoresaber".equals(platform) ? 160 : 0;
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
                if (throttleMs > 0) {
                    try {
                        Thread.sleep(throttleMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.info("{} gap fill complete", platform);
    }
}
