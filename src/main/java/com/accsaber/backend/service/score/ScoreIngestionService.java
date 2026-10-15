package com.accsaber.backend.service.score;

import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
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
import com.accsaber.backend.model.entity.map.LeaderboardPlatform;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.repository.map.MapDifficultyLeaderboardAliasRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.infra.MetricsService;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.PlayerImportService;
import com.accsaber.backend.util.PlatformScoreMapper;

import io.micrometer.core.instrument.Counter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreIngestionService {

    private final ScoreService scoreService;
    private final PlayerImportService playerImportService;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyLeaderboardAliasRepository aliasRepository;
    private final ScoreGapFillService scoreGapFillService;
    private final ModifierCacheService modifierCacheService;
    private final PlatformProperties properties;
    @Qualifier("ingestionScheduler")
    private final ScheduledExecutorService ingestionScheduler;
    private final MetricsService metricsService;
    private final DuplicateUserService duplicateUserService;
    private final ScoreSaberClient scoreSaberClient;
    private final CampaignScoreGate campaignScoreGate;

    private volatile Set<String> rankedBlIds = Set.of();
    private volatile Set<String> rankedSsIds = Set.of();

    @PostConstruct
    public void init() {
        refreshRankedLeaderboardIds();
    }

    public void handleBeatLeaderScore(BeatLeaderScoreResponse blScore) {
        boolean onRankedLeaderboard = rankedBlIds.contains(blScore.getLeaderboardId());
        if (!onRankedLeaderboard && !campaignScoreGate.matchesBlLeaderboard(blScore.getLeaderboardId())) {
            return;
        }
        if (blScore.getPlayer() == null || blScore.getPlayer().getId() == null) {
            log.warn("Received BL score with missing player data for leaderboard {}, skipping",
                    blScore.getLeaderboardId());
            return;
        }

        boolean ranked = onRankedLeaderboard && !PlatformScoreMapper.hasBannedModifier(blScore.getModifiers());
        ingest("BL", ranked, metricsService.getBlScoresIngested(), () -> {
            Long userId = duplicateUserService.resolvePrimaryUserId(
                    Long.parseLong(blScore.getPlayer().getId()));
            if (!ranked && !campaignScoreGate.isParticipant(userId)) {
                return null;
            }
            Optional<MapDifficulty> diffOpt = mapDifficultyRepository
                    .findByBlLeaderboardId(blScore.getLeaderboardId());
            if (diffOpt.isEmpty()) {
                return null;
            }
            return PlatformScoreMapper.fromBeatLeader(
                    blScore, diffOpt.get().getId(), userId, modifierCacheService.getModifierCodeToId());
        });
    }

    public void handleScoreSaberScore(ScoreSaberScoreResponse ssScore, ScoreSaberScoreStats scoreStats,
            Long userId, String ssLeaderboardId) {
        boolean onRankedLeaderboard = rankedSsIds.contains(ssLeaderboardId);
        if (!onRankedLeaderboard && !campaignScoreGate.matchesSsLeaderboard(ssLeaderboardId)) {
            return;
        }

        boolean ranked = onRankedLeaderboard && !PlatformScoreMapper.hasBannedModifier(ssScore.getMods());
        ingest("SS", ranked, metricsService.getSsScoresIngested(), () -> {
            Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
            if (!ranked && !campaignScoreGate.isParticipant(resolvedUserId)) {
                return null;
            }
            Optional<MapDifficulty> diffOpt = mapDifficultyRepository
                    .findBySsLeaderboardId(ssLeaderboardId);
            if (diffOpt.isEmpty()) {
                return null;
            }
            return PlatformScoreMapper.fromScoreSaber(
                    ssScore, resolveScoreStats(ssScore, scoreStats), diffOpt.get().getId(), resolvedUserId,
                    modifierCacheService.getModifierCodeToId());
        });
    }

    @FunctionalInterface
    private interface ScoreRequestBuilder {
        SubmitScoreRequest build();
    }

    private void ingest(String platform, boolean ranked, Counter ingested, ScoreRequestBuilder builder) {
        ingestionScheduler.execute(() -> {
            try {
                SubmitScoreRequest request = builder.build();
                if (request == null) {
                    return;
                }
                if (ranked) {
                    playerImportService.ensurePlayerExists(request.getUserId());
                    metricsService.getScoreProcessingTimer().record(() -> scoreService.submit(request));
                    ingested.increment();
                    log.info("Ingested {} score for player {} on difficulty {}", platform, request.getUserId(),
                            request.getMapDifficultyId());
                } else {
                    submitCampaignScoreQuietly(request, platform);
                }
            } catch (Exception e) {
                log.error("Error handling {} score: {}", platform, e.getMessage());
            }
        });
    }

    private void submitCampaignScoreQuietly(SubmitScoreRequest request, String platform) {
        try {
            scoreService.submitCampaignScore(request);
            log.info("Ingested {} campaign score for player {} on difficulty {}", platform, request.getUserId(),
                    request.getMapDifficultyId());
        } catch (ValidationException e) {
            log.debug("Dropped {} campaign score for player {} on difficulty {}: {}", platform, request.getUserId(),
                    request.getMapDifficultyId(), e.getMessage());
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

        Set<String> blIds = ranked.stream()
                .map(MapDifficulty::getBlLeaderboardId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));
        blIds.addAll(aliasRepository.findRankedBlLeaderboardIds());
        rankedBlIds = blIds;

        Set<String> ssIds = ranked.stream()
                .map(MapDifficulty::getSsLeaderboardId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));
        ssIds.addAll(aliasRepository.findRankedSsLeaderboardIds());
        rankedSsIds = ssIds;

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
        runGapFill(platform, disconnectedAt, "beatleader".equals(platform)
                ? LeaderboardPlatform.BEATLEADER
                : LeaderboardPlatform.SCORESABER, false);
    }

    @Async("backfillExecutor")
    public CompletableFuture<Void> gapFillSince(Instant since, LeaderboardPlatform platform) {
        String label = platform == null ? "all platforms" : platform.name().toLowerCase();
        runGapFill(label, since, platform, true);
        return CompletableFuture.completedFuture(null);
    }

    private void runGapFill(String label, Instant since, LeaderboardPlatform platform, boolean enrichOnly) {
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrue(MapDifficultyStatus.RANKED);
        log.info("Starting {} gap fill from {} across {} ranked difficulties", label, since, ranked.size());

        int throttleMs = platform == LeaderboardPlatform.BEATLEADER ? 0 : 160;
        for (MapDifficulty difficulty : ranked) {
            boolean relevant = (platform != LeaderboardPlatform.SCORESABER
                    && difficulty.getBlLeaderboardId() != null)
                    || (platform != LeaderboardPlatform.BEATLEADER
                            && difficulty.getSsLeaderboardId() != null);
            if (relevant) {
                try {
                    scoreGapFillService.gapFillDifficulty(difficulty, since, platform, enrichOnly);
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
        log.info("{} gap fill complete", label);
    }
}
