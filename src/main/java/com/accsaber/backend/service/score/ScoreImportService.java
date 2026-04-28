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
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.PlayerImportService;
import com.accsaber.backend.service.skill.SkillService;
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
    private final ScoreModifierLinkRepository scoreModifierLinkRepository;
    private final ModifierRepository modifierRepository;
    private final UserRepository userRepository;
    private final ModifierCacheService modifierCacheService;
    private final StatisticsService statisticsService;
    private final OverallStatisticsService overallStatisticsService;
    private final RankingService rankingService;
    private final MilestoneEvaluationService milestoneEvaluationService;
    private final MapDifficultyStatisticsService mapDifficultyStatisticsService;
    private final ScoreRankingService scoreRankingService;
    private final DuplicateUserService duplicateUserService;
    private final SkillService skillService;

    @Autowired
    @Qualifier("backfillExecutor")
    private Executor backfillExecutor;

    private static final int MAX_CONCURRENT_DIFFICULTIES = 3;
    private static final int MAX_CONCURRENT_SCORE_FETCHES_PER_USER = 20;
    private static final int MAX_CONCURRENT_USERS = 2;

    @Value("${accsaber.backfill.gap-fill-page-delay-ms:125}")
    private long gapFillPageDelayMs;

    public void backfillDifficulty(MapDifficulty difficulty) {
        Set<Long> affectedUserIds = importDifficulty(difficulty);
        if (affectedUserIds == null) {
            return;
        }
        batchRecalculateAfterBackfill(difficulty, affectedUserIds, true);
    }

    private Set<Long> importDifficulty(MapDifficulty difficulty) {
        BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping backfill", difficulty.getId());
            return null;
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
                "Backfill import done for difficulty {}: {} BL scores, {} SS scores ({} affected users)",
                difficulty.getId(), blImported, ssImported, affectedUserIds.size());

        return affectedUserIds;
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
    public void backfillDifficultiesAsync(List<UUID> mapDifficultyIds) {
        log.info("Starting parallel backfill for {} difficulties", mapDifficultyIds.size());
        long start = System.currentTimeMillis();
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_DIFFICULTIES);
        java.util.Map<MapDifficulty, Set<Long>> imported = new java.util.concurrent.ConcurrentHashMap<>();

        List<Thread> threads = mapDifficultyIds.stream()
                .map(id -> Thread.startVirtualThread(() -> {
                    semaphore.acquireUninterruptibly();
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
                    } finally {
                        semaphore.release();
                    }
                }))
                .toList();

        joinAll(threads);

        coalescedRecalcAfterBatchBackfill(imported, true);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Parallel backfill complete for {} difficulties in {}s", mapDifficultyIds.size(), elapsed / 1000);
    }

    @Async("taskExecutor")
    public void backfillAllRankedDifficulties() {
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED);
        log.info("Starting parallel backfill for {} ranked difficulties", ranked.size());
        long start = System.currentTimeMillis();
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_DIFFICULTIES);
        java.util.Map<MapDifficulty, Set<Long>> imported = new java.util.concurrent.ConcurrentHashMap<>();

        List<Thread> threads = ranked.stream()
                .map(difficulty -> Thread.startVirtualThread(() -> {
                    semaphore.acquireUninterruptibly();
                    try {
                        Set<Long> users = importDifficulty(difficulty);
                        if (users != null) {
                            imported.put(difficulty, users);
                        }
                    } catch (Exception e) {
                        log.error("Error backfilling difficulty {}: {}", difficulty.getId(), e.getMessage());
                    } finally {
                        semaphore.release();
                    }
                }))
                .toList();

        joinAll(threads);

        coalescedRecalcAfterBatchBackfill(imported, true);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Parallel backfill of all {} ranked difficulties complete in {}s", ranked.size(), elapsed / 1000);
        log.info("Running post-backfill rank repair");
        scoreRankingService.reassignAllRanks();
    }

    private static void joinAll(List<Thread> threads) {
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void backfillUser(Long userId) {
        if (userRepository.findByIdAndActiveTrue(userId).isEmpty()) {
            log.warn("Cannot backfill: user {} not found or inactive", userId);
            return;
        }
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED).stream()
                .filter(d -> d.getBlLeaderboardId() != null)
                .toList();
        if (ranked.isEmpty()) {
            log.info("No ranked BL difficulties for user {} backfill", resolvedUserId);
            return;
        }

        log.info("Starting user backfill for {} across {} ranked BL difficulties", resolvedUserId, ranked.size());
        long start = System.currentTimeMillis();

        Map<String, UUID> modifiers = modifierCacheService.getModifierCodeToId();
        Set<MapDifficulty> affectedDifficulties = java.util.concurrent.ConcurrentHashMap.newKeySet();
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_SCORE_FETCHES_PER_USER);

        List<Thread> threads = ranked.stream()
                .map(difficulty -> Thread.startVirtualThread(() -> {
                    semaphore.acquireUninterruptibly();
                    try {
                        if (reconcileUserScoreForDifficulty(resolvedUserId, difficulty, modifiers)) {
                            affectedDifficulties.add(difficulty);
                        }
                    } catch (Exception e) {
                        log.error("User backfill failed for user {} difficulty {}: {}",
                                resolvedUserId, difficulty.getId(), e.getMessage());
                    } finally {
                        semaphore.release();
                    }
                }))
                .toList();

        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        log.info("User backfill fetch done for {}: {} affected difficulties. Running recalc...",
                resolvedUserId, affectedDifficulties.size());
        batchRecalculateAfterUserBackfill(resolvedUserId, affectedDifficulties);

        long elapsed = System.currentTimeMillis() - start;
        log.info("User backfill complete for {} in {}s", resolvedUserId, elapsed / 1000);
    }

    @Async("taskExecutor")
    public void backfillUserAsync(Long userId) {
        backfillUser(userId);
    }

    @Async("taskExecutor")
    public void backfillUsersAsync(List<Long> userIds) {
        log.info("Starting parallel user backfill for {} users", userIds.size());
        long start = System.currentTimeMillis();
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_USERS);

        List<Thread> threads = userIds.stream()
                .map(userId -> Thread.startVirtualThread(() -> {
                    semaphore.acquireUninterruptibly();
                    try {
                        backfillUser(userId);
                    } catch (Exception e) {
                        log.error("Error backfilling user {}: {}", userId, e.getMessage());
                    } finally {
                        semaphore.release();
                    }
                }))
                .toList();

        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        long elapsed = System.currentTimeMillis() - start;
        log.info("Parallel user backfill complete for {} users in {}s", userIds.size(), elapsed / 1000);
    }

    private boolean reconcileUserScoreForDifficulty(Long userId, MapDifficulty difficulty,
            Map<String, UUID> modifiers) {
        Optional<BeatLeaderScoreResponse> blOpt = beatLeaderClient.getPlayerScoreOnLeaderboard(
                String.valueOf(userId), difficulty.getBlLeaderboardId());
        if (blOpt.isEmpty())
            return false;
        BeatLeaderScoreResponse blScore = blOpt.get();
        if (blScore.getBaseScore() == null)
            return false;
        if (PlatformScoreMapper.hasBannedModifier(blScore.getModifiers()))
            return false;

        Optional<Score> existing = scoreRepository
                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(userId, difficulty.getId());
        if (existing.isPresent() && existing.get().getScoreNoMods() > blScore.getBaseScore()) {
            return false;
        }

        BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping user backfill entry", difficulty.getId());
            return false;
        }

        if (blScore.getPlayer() == null || blScore.getPlayer().getId() == null) {
            blScore.setPlayer(stubPlayer(userId));
        }

        Long affected = importBeatLeaderScore(blScore, difficulty, complexity, modifiers, true);
        return affected != null;
    }

    private static BeatLeaderScoreResponse.Player stubPlayer(Long userId) {
        BeatLeaderScoreResponse.Player p = new BeatLeaderScoreResponse.Player();
        p.setId(String.valueOf(userId));
        return p;
    }

    private void batchRecalculateAfterUserBackfill(Long userId, Set<MapDifficulty> affectedDifficulties) {
        if (affectedDifficulties.isEmpty()) {
            log.info("No new/changed scores for user {} - skipping user backfill recalc", userId);
            return;
        }

        Set<UUID> affectedCategoryIds = affectedDifficulties.stream()
                .map(d -> d.getCategory().getId())
                .collect(java.util.stream.Collectors.toSet());
        boolean touchesOverall = affectedDifficulties.stream()
                .anyMatch(d -> d.getCategory().isCountForOverall());

        for (MapDifficulty difficulty : affectedDifficulties) {
            try {
                scoreRankingService.reassignRanks(difficulty.getId());
                mapDifficultyStatisticsService.recalculate(difficulty, null);
            } catch (Exception e) {
                log.error("Difficulty recalc failed for difficulty {} during user {} backfill: {}",
                        difficulty.getId(), userId, e.getMessage());
            }
        }

        for (UUID categoryId : affectedCategoryIds) {
            try {
                statisticsService.recalculate(userId, categoryId, false);
                rankingService.updateRankings(categoryId);
                skillService.upsertSkill(userId, categoryId);
            } catch (Exception e) {
                log.error("Category recalc failed for category {} during user {} backfill: {}",
                        categoryId, userId, e.getMessage());
            }
        }

        if (touchesOverall) {
            try {
                overallStatisticsService.updateOverallRankings();
            } catch (Exception e) {
                log.error("Overall ranking recalc failed during user {} backfill: {}", userId, e.getMessage());
            }
        }

        try {
            var evaluation = milestoneEvaluationService.evaluateAllForUser(userId);
            awardMilestoneXp(userId, evaluation);
        } catch (Exception e) {
            log.error("Milestone evaluation failed during user {} backfill: {}", userId, e.getMessage());
        }

        log.info("User backfill recalc complete for user {} ({} difficulties, {} categories)",
                userId, affectedDifficulties.size(), affectedCategoryIds.size());
    }

    @Async("taskExecutor")
    public void startupGapFillAllRankedDifficulties(Instant since) {
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED);
        log.info("Starting parallel gap-fill for {} ranked difficulties since {}", ranked.size(), since);

        long start = System.currentTimeMillis();
        Semaphore semaphore = new Semaphore(MAX_CONCURRENT_DIFFICULTIES);
        java.util.Map<MapDifficulty, Set<Long>> imported = new java.util.concurrent.ConcurrentHashMap<>();

        List<Thread> threads = ranked.stream()
                .map(difficulty -> Thread.startVirtualThread(() -> {
                    semaphore.acquireUninterruptibly();
                    try {
                        Set<Long> users = startupGapFillImport(difficulty, since);
                        if (users != null) {
                            imported.put(difficulty, users);
                        }
                    } catch (Exception e) {
                        log.error("Error gap-filling difficulty {}: {}", difficulty.getId(), e.getMessage());
                    } finally {
                        semaphore.release();
                    }
                }))
                .toList();

        joinAll(threads);

        coalescedRecalcAfterBatchBackfill(imported, false);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Parallel gap-fill complete for {} difficulties in {}s", ranked.size(), elapsed / 1000);
        log.info("Running post-gap-fill rank repair");
        scoreRankingService.reassignAllRanks();
    }

    private Set<Long> startupGapFillImport(MapDifficulty difficulty, Instant since) {
        BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping gap-fill", difficulty.getId());
            return null;
        }

        Map<String, UUID> modifiers = modifierCacheService.getModifierCodeToId();
        Set<Long> affectedUserIds = new HashSet<>();

        if (difficulty.getBlLeaderboardId() != null) {
            affectedUserIds.addAll(gapFillFromBeatLeader(difficulty, complexity, modifiers, since));
        }

        return affectedUserIds;
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

    private void coalescedRecalcAfterBatchBackfill(java.util.Map<MapDifficulty, Set<Long>> imported,
            boolean syncRankWhenSet) {
        if (imported.isEmpty()) {
            log.info("No new scores imported across batch - skipping coalesced recalc");
            return;
        }

        java.util.Map<UUID, Set<Long>> usersByCategory = new java.util.HashMap<>();
        Set<Long> allAffectedUsers = new HashSet<>();
        java.util.Map<Long, Boolean> userTouchesOverall = new java.util.HashMap<>();
        boolean anyOverall = false;

        for (java.util.Map.Entry<MapDifficulty, Set<Long>> e : imported.entrySet()) {
            MapDifficulty difficulty = e.getKey();
            Set<Long> users = e.getValue();
            if (users.isEmpty()) continue;
            UUID categoryId = difficulty.getCategory().getId();
            usersByCategory.computeIfAbsent(categoryId, k -> new HashSet<>()).addAll(users);
            allAffectedUsers.addAll(users);
            if (difficulty.getCategory().isCountForOverall()) {
                anyOverall = true;
                for (Long u : users) userTouchesOverall.put(u, Boolean.TRUE);
            }
        }

        log.info("Coalesced recalc: {} difficulties, {} unique users, {} categories",
                imported.size(), allAffectedUsers.size(), usersByCategory.size());

        List<CompletableFuture<Void>> diffFutures = imported.keySet().stream()
                .filter(d -> !imported.get(d).isEmpty())
                .map(difficulty -> CompletableFuture.runAsync(() -> {
                    try {
                        if (syncRankWhenSet) {
                            scoreRankingService.reassignRanksForBackfill(difficulty.getId());
                        } else {
                            scoreRankingService.reassignRanks(difficulty.getId());
                        }
                        mapDifficultyStatisticsService.recalculate(difficulty, null);
                    } catch (Exception ex) {
                        log.error("Per-difficulty recalc failed for {}: {}",
                                difficulty.getId(), ex.getMessage());
                    }
                }, backfillExecutor))
                .toList();
        diffFutures.forEach(CompletableFuture::join);

        List<CompletableFuture<Void>> statsFutures = new ArrayList<>();
        for (java.util.Map.Entry<UUID, Set<Long>> e : usersByCategory.entrySet()) {
            UUID categoryId = e.getKey();
            for (Long userId : e.getValue()) {
                statsFutures.add(CompletableFuture.runAsync(() -> {
                    try {
                        statisticsService.recalculate(userId, categoryId, false, false);
                    } catch (Exception ex) {
                        log.error("Stats recalc failed for user {} category {}: {}",
                                userId, categoryId, ex.getMessage());
                    }
                }, backfillExecutor));
            }
        }
        statsFutures.forEach(CompletableFuture::join);

        List<CompletableFuture<Void>> userFutures = allAffectedUsers.stream()
                .map(userId -> CompletableFuture.runAsync(() -> {
                    try {
                        if (Boolean.TRUE.equals(userTouchesOverall.get(userId))) {
                            overallStatisticsService.recalculate(userId, false);
                        }
                        var evaluation = milestoneEvaluationService.evaluateAllForUser(userId);
                        awardMilestoneXp(userId, evaluation);
                    } catch (Exception ex) {
                        log.error("Per-user post-backfill work failed for user {}: {}",
                                userId, ex.getMessage());
                    }
                }, backfillExecutor))
                .toList();
        userFutures.forEach(CompletableFuture::join);

        for (java.util.Map.Entry<UUID, Set<Long>> e : usersByCategory.entrySet()) {
            UUID categoryId = e.getKey();
            try {
                rankingService.updateRankings(categoryId);
            } catch (Exception ex) {
                log.error("Category ranking update failed for {}: {}", categoryId, ex.getMessage());
            }
            for (Long userId : e.getValue()) {
                try {
                    skillService.upsertSkill(userId, categoryId);
                } catch (Exception ex) {
                    log.error("Skill upsert failed for user {} category {}: {}", userId, categoryId, ex.getMessage());
                }
            }
        }

        if (anyOverall) {
            try {
                overallStatisticsService.updateOverallRankings();
            } catch (Exception ex) {
                log.error("Overall ranking update failed: {}", ex.getMessage());
            }
        }

        log.info("Coalesced recalc complete: {} (user,category) pairs, {} users",
                statsFutures.size(), allAffectedUsers.size());
    }

    private void batchRecalculateAfterBackfill(MapDifficulty difficulty, Set<Long> affectedUserIds,
            boolean syncRankWhenSet) {
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

        if (syncRankWhenSet) {
            scoreRankingService.reassignRanksForBackfill(difficulty.getId());
        } else {
            scoreRankingService.reassignRanks(difficulty.getId());
        }
        mapDifficultyStatisticsService.recalculate(difficulty, null);
        rankingService.updateRankings(categoryId);
        for (Long userId : affectedUserIds) {
            try {
                skillService.upsertSkill(userId, categoryId);
            } catch (Exception e) {
                log.error("Skill upsert failed for user {} category {}: {}", userId, categoryId, e.getMessage());
            }
        }
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
            List<BeatLeaderScoreResponse> scores;
            try {
                scores = beatLeaderClient.getLeaderboardScores(
                        difficulty.getBlLeaderboardId(), page, pageSize);
            } catch (Exception e) {
                log.error("BL backfill failed on page {} for difficulty {}: {}",
                        page, difficulty.getId(), e.getMessage());
                throw new RuntimeException("BL backfill aborted on page " + page, e);
            }

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
        }
        return affected;
    }

    private static final long SS_PAGE_DELAY_MS = 100;

    private Set<Long> backfillFromScoreSaber(MapDifficulty difficulty, BigDecimal complexity,
            Map<String, UUID> modifiers) {
        Set<Long> affected = new HashSet<>();
        int page = 1;
        int totalPages = Integer.MAX_VALUE;

        while (page <= totalPages) {
            ScoreSaberScoresPage scoresPage;
            try {
                scoresPage = scoreSaberClient.getLeaderboardScores(
                        difficulty.getSsLeaderboardId(), page);
            } catch (Exception e) {
                log.error("SS backfill failed on page {} for difficulty {}: {}",
                        page, difficulty.getId(), e.getMessage());
                throw new RuntimeException("SS backfill aborted on page " + page, e);
            }

            if (scoresPage == null || scoresPage.getScores() == null || scoresPage.getScores().isEmpty())
                break;

            if (totalPages == Integer.MAX_VALUE && scoresPage.getMetadata() != null
                    && scoresPage.getMetadata().getTotal() != null
                    && scoresPage.getMetadata().getItemsPerPage() != null
                    && scoresPage.getMetadata().getItemsPerPage() > 0) {
                totalPages = (int) Math.ceil((double) scoresPage.getMetadata().getTotal()
                        / scoresPage.getMetadata().getItemsPerPage());
            }

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
                Thread.sleep(SS_PAGE_DELAY_MS);
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
                    reconcileModifierLinks(existingScore.get(), blScore.getModifiers());
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
                ? blScore.getPlayCount()
                : null);
        if (score.getMaxCombo() == null && blScore.getMaxCombo() != null && blScore.getMaxCombo() > 0) {
            score.setMaxCombo(blScore.getMaxCombo());
        }
        if (score.getBadCuts() == null && blScore.getBadCuts() != null) {
            score.setBadCuts(blScore.getBadCuts());
        }
        if (score.getMisses() == null && blScore.getMissedNotes() != null) {
            score.setMisses(blScore.getMissedNotes());
        }
        if (score.getHmd() == null && blScore.getHmd() != null) {
            score.setHmd(com.accsaber.backend.util.HmdMapper.fromBeatLeaderId(blScore.getHmd()));
        }
        if (score.getTimeSet() == null && blScore.getTimepost() != null && blScore.getTimepost() > 0) {
            score.setTimeSet(Instant.ofEpochSecond(blScore.getTimepost()));
        }
        scoreRepository.save(score);
        reconcileModifierLinks(score, blScore.getModifiers());
        log.debug("Enriched score {} with BL data (blScoreId={})", score.getId(), blScore.getId());
    }

    private void reconcileModifierLinks(Score score, String modifiersStr) {
        if (modifiersStr == null || modifiersStr.isBlank())
            return;
        Set<UUID> existingModifierIds = scoreModifierLinkRepository.findByScore_Id(score.getId()).stream()
                .map(l -> l.getModifier().getId())
                .collect(java.util.stream.Collectors.toSet());
        Map<String, UUID> codeToId = modifierCacheService.getModifierCodeToId();
        List<ScoreModifierLink> toAdd = new ArrayList<>();
        for (String code : modifiersStr.split(",")) {
            String trimmed = code.trim();
            if (trimmed.isEmpty())
                continue;
            UUID modifierId = codeToId.get(trimmed);
            if (modifierId == null || existingModifierIds.contains(modifierId))
                continue;
            Modifier modifier = modifierRepository.findById(modifierId).orElse(null);
            if (modifier == null)
                continue;
            toAdd.add(ScoreModifierLink.builder().score(score).modifier(modifier).build());
        }
        if (!toAdd.isEmpty()) {
            scoreModifierLinkRepository.saveAll(toAdd);
            log.debug("Added {} missing modifier links to score {}", toAdd.size(), score.getId());
        }
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
