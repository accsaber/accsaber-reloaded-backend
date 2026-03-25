package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.config.PlatformProperties;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.PlayerImportService;
import com.accsaber.backend.service.stats.OverallStatisticsService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;
import com.accsaber.backend.util.PlatformScoreMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreImportService {

    private final BeatLeaderClient beatLeaderClient;
    private final ScoreSaberClient scoreSaberClient;
    private final ScoreService scoreService;
    private final PlayerImportService playerImportService;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyComplexityService mapComplexityService;
    private final ScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final ModifierCacheService modifierCacheService;
    private final StatisticsService statisticsService;
    private final OverallStatisticsService overallStatisticsService;
    private final RankingService rankingService;
    private final MilestoneEvaluationService milestoneEvaluationService;
    private final MapDifficultyStatisticsService mapDifficultyStatisticsService;
    private final ScoreRankingService scoreRankingService;
    private final DuplicateUserService duplicateUserService;
    private final PlatformProperties properties;

    @Autowired
    @Qualifier("backfillExecutor")
    private Executor backfillExecutor;

    @Value("${accsaber.backfill.gap-fill-page-delay-ms:125}")
    private long gapFillPageDelayMs;

    public void backfillDifficulty(MapDifficulty difficulty) {
        BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping backfill", difficulty.getId());
            return;
        }

        Map<String, UUID> modifiers = modifierCacheService.getModifierCodeToId();
        Set<Long> affectedUserIds = new HashSet<>();
        int blImported = 0;
        int ssImported = 0;

        if (difficulty.getBlLeaderboardId() != null) {
            Set<Long> blUsers = backfillFromBeatLeader(difficulty, complexity, modifiers);
            blImported = blUsers.size();
            affectedUserIds.addAll(blUsers);
        }

        if (difficulty.getSsLeaderboardId() != null) {
            Set<Long> ssUsers = backfillFromScoreSaber(difficulty, complexity, modifiers);
            ssImported = ssUsers.size();
            affectedUserIds.addAll(ssUsers);
        }

        log.info(
                "Backfill import done for difficulty {}: {} BL scores, {} SS scores. Running batch recalc for {} affected users...",
                difficulty.getId(), blImported, ssImported, affectedUserIds.size());

        batchRecalculateAfterBackfill(difficulty, affectedUserIds);
    }

    @Async("taskExecutor")
    public void backfillDifficultyAsync(UUID mapDifficultyId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithCategory(mapDifficultyId)
                .orElse(null);
        if (difficulty == null) {
            log.warn("Cannot backfill: difficulty {} not found or inactive", mapDifficultyId);
            return;
        }
        backfillDifficulty(difficulty);
    }

    @Async("taskExecutor")
    public void backfillDifficultiesSequentiallyAsync(List<UUID> mapDifficultyIds) {
        log.info("Starting sequential backfill for {} difficulties", mapDifficultyIds.size());
        for (UUID id : mapDifficultyIds) {
            try {
                MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrueWithCategory(id)
                        .orElse(null);
                if (difficulty == null) {
                    log.warn("Cannot backfill: difficulty {} not found or inactive", id);
                    continue;
                }
                backfillDifficulty(difficulty);
                Thread.sleep(properties.getBackfillPageDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Sequential backfill interrupted");
                return;
            } catch (Exception e) {
                log.error("Error backfilling difficulty {}: {}", id, e.getMessage());
            }
        }
        log.info("Sequential backfill complete for {} difficulties", mapDifficultyIds.size());
    }

    @Async("taskExecutor")
    public void backfillAllRankedDifficulties() {
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED);
        log.info("Starting backfill for {} ranked difficulties", ranked.size());

        for (MapDifficulty difficulty : ranked) {
            try {
                backfillDifficulty(difficulty);
                Thread.sleep(properties.getBackfillPageDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Backfill interrupted");
                return;
            } catch (Exception e) {
                log.error("Error backfilling difficulty {}: {}", difficulty.getId(), e.getMessage());
            }
        }
        log.info("Backfill of all ranked difficulties complete");
    }

    @Async("taskExecutor")
    public void startupGapFillAllRankedDifficulties(Instant since) {
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED);
        log.info("Starting startup gap-fill for {} ranked difficulties since {}", ranked.size(), since);

        for (MapDifficulty difficulty : ranked) {
            try {
                startupGapFillDifficulty(difficulty, since);
                Thread.sleep(properties.getBackfillPageDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Startup gap-fill interrupted");
                return;
            } catch (Exception e) {
                log.error("Error gap-filling difficulty {}: {}", difficulty.getId(), e.getMessage());
            }
        }
        log.info("Startup gap-fill complete");
    }

    private void startupGapFillDifficulty(MapDifficulty difficulty, Instant since) {
        BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping gap-fill", difficulty.getId());
            return;
        }

        Map<String, UUID> modifiers = modifierCacheService.getModifierCodeToId();
        Set<Long> affectedUserIds = new HashSet<>();

        if (difficulty.getBlLeaderboardId() != null) {
            affectedUserIds.addAll(gapFillFromBeatLeader(difficulty, complexity, modifiers, since));
        }

        batchRecalculateAfterBackfill(difficulty, affectedUserIds);
    }

    private Set<Long> gapFillFromBeatLeader(MapDifficulty difficulty, BigDecimal complexity,
            Map<String, UUID> modifiers, Instant since) {
        Set<Long> affected = new HashSet<>();
        int page = 1;
        int pageSize = 100;
        long sinceEpoch = since.getEpochSecond();

        while (true) {
            List<BeatLeaderScoreResponse> scores = beatLeaderClient.getLeaderboardScoresSortedByDate(
                    difficulty.getBlLeaderboardId(), page, pageSize);

            if (scores.isEmpty())
                break;

            List<BeatLeaderScoreResponse> recent = new ArrayList<>();
            boolean reachedThreshold = false;
            for (BeatLeaderScoreResponse score : scores) {
                if (score.getTimepost() != null && score.getTimepost() <= sinceEpoch) {
                    reachedThreshold = true;
                    break;
                }
                recent.add(score);
            }

            recent.stream()
                    .map(s -> CompletableFuture.supplyAsync(
                            () -> importBeatLeaderScore(s, difficulty, complexity, modifiers, true),
                            backfillExecutor))
                    .toList()
                    .stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .forEach(affected::add);

            if (reachedThreshold || scores.size() < pageSize)
                break;
            page++;

            try {
                Thread.sleep(gapFillPageDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return affected;
    }

    public void gapFillDifficulty(MapDifficulty difficulty, Instant since) {
        Map<String, UUID> modifiers = modifierCacheService.getModifierCodeToId();
        long sinceEpoch = since.getEpochSecond();

        if (difficulty.getBlLeaderboardId() != null) {
            List<BeatLeaderScoreResponse> blScores = beatLeaderClient.getRecentScores(
                    difficulty.getBlLeaderboardId(), sinceEpoch);
            for (BeatLeaderScoreResponse blScore : blScores) {
                importBeatLeaderScore(blScore, difficulty, modifiers);
            }
        }

        if (difficulty.getSsLeaderboardId() != null) {
            ScoreSaberScoresPage page = scoreSaberClient.getRecentScores(
                    difficulty.getSsLeaderboardId(), sinceEpoch);
            if (page != null && page.getScores() != null) {
                for (ScoreSaberScoreResponse ssScore : page.getScores()) {
                    try {
                        if (ssScore.getTimeSet() == null)
                            continue;
                        if (Instant.parse(ssScore.getTimeSet()).getEpochSecond() <= sinceEpoch)
                            continue;
                    } catch (Exception e) {
                        log.warn("Could not parse SS score timeSet '{}', including it in gap fill anyway",
                                ssScore.getTimeSet());
                    }
                    importScoreSaberScore(ssScore, difficulty, modifiers);
                }
            }
        }
    }

    private void batchRecalculateAfterBackfill(MapDifficulty difficulty, Set<Long> affectedUserIds) {
        if (affectedUserIds.isEmpty()) {
            log.info("No new scores imported for difficulty {} - skipping batch recalc", difficulty.getId());
            return;
        }

        UUID categoryId = difficulty.getCategory().getId();
        log.info("Batch recalc for difficulty {}: {} affected users, category {}",
                difficulty.getId(), affectedUserIds.size(), categoryId);

        List<CompletableFuture<Void>> futures = affectedUserIds.stream()
                .map(userId -> CompletableFuture.runAsync(() -> {
                    try {
                        statisticsService.recalculate(userId, categoryId, false);
                        var evaluation = milestoneEvaluationService.evaluateAllForUser(userId);
                        awardMilestoneXp(userId, evaluation);
                    } catch (Exception e) {
                        log.error("Batch recalc failed for user {} on difficulty {}: {}", userId, difficulty.getId(),
                                e.getMessage());
                    }
                }, backfillExecutor))
                .toList();

        futures.forEach(CompletableFuture::join);

        scoreRankingService.reassignRanksForBackfill(difficulty.getId());
        mapDifficultyStatisticsService.recalculate(difficulty, null);
        rankingService.updateRankings(categoryId);
        if (difficulty.getCategory().isCountForOverall()) {
            overallStatisticsService.updateOverallRankings();
        }
        log.info("Batch recalc complete for difficulty {}", difficulty.getId());
    }

    private void awardMilestoneXp(Long userId, MilestoneEvaluationService.EvaluationResult evaluation) {
        if (evaluation.completedMilestones().isEmpty() && evaluation.completedSets().isEmpty())
            return;

        BigDecimal milestoneXp = evaluation.completedMilestones().stream()
                .map(Milestone::getXp)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal setXp = evaluation.completedSets().stream()
                .map(MilestoneSet::getSetBonusXp)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = milestoneXp.add(setXp);
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            userRepository.addXp(userId, total);
        }
    }

    private Set<Long> backfillFromBeatLeader(MapDifficulty difficulty, BigDecimal complexity,
            Map<String, UUID> modifiers) {
        Set<Long> affected = new HashSet<>();
        int page = 1;
        int pageSize = 100;

        while (true) {
            List<BeatLeaderScoreResponse> scores = beatLeaderClient.getLeaderboardScores(
                    difficulty.getBlLeaderboardId(), page, pageSize);

            if (scores.isEmpty())
                break;

            scores.stream()
                    .map(blScore -> CompletableFuture.supplyAsync(
                            () -> importBeatLeaderScore(blScore, difficulty, complexity, modifiers, true),
                            backfillExecutor))
                    .toList()
                    .stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .forEach(affected::add);

            if (scores.size() < pageSize)
                break;
            page++;

            try {
                Thread.sleep(properties.getBackfillPageDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return affected;
    }

    private static final int SS_BACKFILL_MAX_PAGE_RETRIES = 3;

    private Set<Long> backfillFromScoreSaber(MapDifficulty difficulty, BigDecimal complexity,
            Map<String, UUID> modifiers) {
        Set<Long> affected = new HashSet<>();
        int page = 1;
        int totalPages = Integer.MAX_VALUE;
        int consecutiveFailures = 0;

        while (page <= totalPages) {
            ScoreSaberScoresPage scoresPage = scoreSaberClient.getLeaderboardScores(
                    difficulty.getSsLeaderboardId(), page);

            if (scoresPage == null || scoresPage.getScores() == null) {
                consecutiveFailures++;
                if (consecutiveFailures >= SS_BACKFILL_MAX_PAGE_RETRIES) {
                    log.warn("SS backfill for difficulty {} stopped at page {} after {} consecutive failures",
                            difficulty.getId(), page, SS_BACKFILL_MAX_PAGE_RETRIES);
                    break;
                }
                log.warn("SS backfill page {} failed for difficulty {}, retrying ({}/{})",
                        page, difficulty.getId(), consecutiveFailures, SS_BACKFILL_MAX_PAGE_RETRIES);
                try {
                    Thread.sleep(properties.getBackfillPageDelay() * 2L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            consecutiveFailures = 0;

            if (totalPages == Integer.MAX_VALUE && scoresPage.getMetadata() != null
                    && scoresPage.getMetadata().getTotal() != null
                    && scoresPage.getMetadata().getItemsPerPage() != null
                    && scoresPage.getMetadata().getItemsPerPage() > 0) {
                totalPages = (int) Math.ceil((double) scoresPage.getMetadata().getTotal()
                        / scoresPage.getMetadata().getItemsPerPage());
            }

            if (scoresPage.getScores().isEmpty())
                break;

            scoresPage.getScores().stream()
                    .map(ssScore -> CompletableFuture.supplyAsync(
                            () -> importScoreSaberScore(ssScore, difficulty, complexity, modifiers, true),
                            backfillExecutor))
                    .toList()
                    .stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .forEach(affected::add);

            page++;

            try {
                Thread.sleep(properties.getBackfillPageDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return affected;
    }

    private Long importBeatLeaderScore(BeatLeaderScoreResponse blScore, MapDifficulty difficulty,
            Map<String, UUID> modifiers) {
        return importBeatLeaderScore(blScore, difficulty, null, modifiers, false);
    }

    private Long importBeatLeaderScore(BeatLeaderScoreResponse blScore, MapDifficulty difficulty,
            BigDecimal complexity, Map<String, UUID> modifiers, boolean forBackfill) {
        try {
            if (PlatformScoreMapper.hasBannedModifier(blScore.getModifiers()))
                return null;
            Long userId = duplicateUserService.resolvePrimaryUserId(
                    Long.parseLong(blScore.getPlayer().getId()));
            Optional<Score> existingScore = scoreRepository
                    .findByUser_IdAndMapDifficulty_IdAndActiveTrue(userId, difficulty.getId());
            if (existingScore.isPresent()
                    && Objects.equals(existingScore.get().getScoreNoMods(), blScore.getBaseScore())) {
                if (existingScore.get().getBlScoreId() != null) {
                    return null;
                }
                enrichWithBeatLeaderData(existingScore.get(), blScore);
                return null;
            }
            playerImportService.ensurePlayerExists(userId);
            SubmitScoreRequest request = PlatformScoreMapper.fromBeatLeader(blScore, difficulty.getId(), userId,
                    modifiers);
            if (forBackfill) {
                scoreService.submitForBackfill(request, difficulty, complexity);
            } else {
                scoreService.submit(request);
            }
            return userId;
        } catch (Exception e) {
            log.error("Failed to import BL score {} for difficulty {}: {}",
                    blScore.getId(), difficulty.getId(), e.getMessage());
            return null;
        }
    }

    private void enrichWithBeatLeaderData(Score score, BeatLeaderScoreResponse blScore) {
        score.setBlScoreId(blScore.getId());
        score.setWallHits(blScore.getWallsHit());
        score.setBombHits(blScore.getBombCuts());
        score.setPauses(blScore.getPauses());
        score.setStreak115(blScore.getMaxStreak());
        score.setPlayCount(blScore.getPlayCount() != null && blScore.getPlayCount() > 0
                ? blScore.getPlayCount() : null);
        if (score.getHmd() == null && blScore.getHmd() != null) {
            score.setHmd(com.accsaber.backend.util.HmdMapper.fromBeatLeaderId(blScore.getHmd()));
        }
        if (score.getTimeSet() == null && blScore.getTimepost() != null && blScore.getTimepost() > 0) {
            score.setTimeSet(Instant.ofEpochSecond(blScore.getTimepost()));
        }
        scoreRepository.save(score);
        log.debug("Enriched SS score {} with BL data (blScoreId={})", score.getId(), blScore.getId());
    }

    private Long importScoreSaberScore(ScoreSaberScoreResponse ssScore, MapDifficulty difficulty,
            Map<String, UUID> modifiers) {
        return importScoreSaberScore(ssScore, difficulty, null, modifiers, false);
    }

    private Long importScoreSaberScore(ScoreSaberScoreResponse ssScore, MapDifficulty difficulty,
            BigDecimal complexity, Map<String, UUID> modifiers, boolean forBackfill) {
        try {
            if (PlatformScoreMapper.hasBannedModifier(ssScore.getModifiers()))
                return null;
            Long userId = duplicateUserService.resolvePrimaryUserId(
                    Long.parseLong(ssScore.getLeaderboardPlayerInfo().getId()));
            Optional<Score> existingScore = scoreRepository
                    .findByUser_IdAndMapDifficulty_IdAndActiveTrue(userId, difficulty.getId());
            if (existingScore.isPresent()
                    && Objects.equals(existingScore.get().getScoreNoMods(), ssScore.getBaseScore())) {
                return null;
            }
            playerImportService.ensurePlayerExists(userId);
            SubmitScoreRequest request = PlatformScoreMapper.fromScoreSaber(ssScore, difficulty.getId(), userId,
                    modifiers);
            if (forBackfill) {
                scoreService.submitForBackfill(request, difficulty, complexity);
            } else {
                scoreService.submit(request);
            }
            return userId;
        } catch (Exception e) {
            log.error("Failed to import SS score {} for difficulty {}: {}",
                    ssScore.getId(), difficulty.getId(), e.getMessage());
            return null;
        }
    }
}
