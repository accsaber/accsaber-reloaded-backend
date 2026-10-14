package com.accsaber.backend.service.score;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreStats;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.map.LeaderboardPlatform;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;
import com.accsaber.backend.model.event.LegacyCampaignBackfillEvent;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.campaign.CampaignEvaluationService;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.PlayerImportService;
import com.accsaber.backend.service.score.ScoreRecalculationService.PlayerRecalc;
import com.accsaber.backend.service.songsuggest.SongSuggestService;
import com.accsaber.backend.util.HmdMapper;
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
    private final MilestoneEvaluationService milestoneEvaluationService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final UserCampaignRepository userCampaignRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final MapDifficultyStatisticsService mapDifficultyStatisticsService;
    private final ScoreRankingService scoreRankingService;
    private final DuplicateUserService duplicateUserService;
    private final SongSuggestService songSuggestService;
    private final ScoreRecalculationService scoreRecalculationService;

    private final PlatformScores<BeatLeaderScoreResponse> beatLeader = new BeatLeaderScores();
    private final PlatformScores<ScoreSaberScoreResponse> scoreSaber = new ScoreSaberScores();

    @Autowired
    @Qualifier("backfillExecutor")
    private Executor backfillExecutor;

    private static final int MAX_CONCURRENT_DIFFICULTIES = 3;
    private static final int MAX_CONCURRENT_SCORE_FETCHES_PER_USER = 20;
    private static final int MAX_CONCURRENT_USERS = 2;
    private static final int PAGE_SIZE = 100;

    @Value("${accsaber.backfill.gap-fill-page-delay-ms:125}")
    private long gapFillPageDelayMs;

    @Value("${accsaber.backfill.scoresaber-enabled:true}")
    private boolean scoreSaberBackfillEnabled = true;

    private enum Cut {
        KEEP, SKIP, STOP
    }

    private record ScorePage<S>(List<S> scores, boolean last) {
    }

    private record ImportTarget(MapDifficulty difficulty, Double complexity, Map<String, UUID> modifiers) {
    }

    public void backfillDifficulty(MapDifficulty difficulty) {
        Set<Long> affectedUserIds = importDifficulty(difficulty);
        if (affectedUserIds == null) {
            return;
        }
        recalculateAfterImport(Map.of(difficulty, affectedUserIds), true);
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
            Set<Long> blUsers = backfillFrom(beatLeader, difficulty.getBlLeaderboardId(), target);
            blImported = blUsers.size();
            affectedUserIds.addAll(blUsers);
        }

        if (scoreSaberBackfillEnabled && difficulty.getSsLeaderboardId() != null) {
            Set<Long> ssUsers = backfillFrom(scoreSaber, difficulty.getSsLeaderboardId(), target);
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
        if (platform == LeaderboardPlatform.SCORESABER && !scoreSaberBackfillEnabled) {
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
                ? backfillFrom(beatLeader, leaderboardId, target)
                : backfillFrom(scoreSaber, leaderboardId, target);
        log.info("Alias backfill imported {} scores from {} leaderboard {} into difficulty {}",
                affectedUserIds.size(), platform, leaderboardId, mapDifficultyId);
        recalculateAfterImport(Map.of(difficulty, affectedUserIds), false);
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> backfillDifficultiesAsync(List<UUID> mapDifficultyIds) {
        log.info("Starting parallel backfill for {} difficulties", mapDifficultyIds.size());
        long start = System.currentTimeMillis();
        Map<MapDifficulty, Set<Long>> imported = new ConcurrentHashMap<>();

        forEachBounded(mapDifficultyIds, MAX_CONCURRENT_DIFFICULTIES, id -> {
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

        recalculateAfterImport(imported, true);

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

        forEachBounded(ranked, MAX_CONCURRENT_DIFFICULTIES, difficulty -> {
            try {
                Set<Long> users = importDifficulty(difficulty);
                if (users != null) {
                    imported.put(difficulty, users);
                }
            } catch (Exception e) {
                log.error("Error backfilling difficulty {}: {}", difficulty.getId(), e.getMessage());
            }
        });

        recalculateAfterImport(imported, true);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Parallel backfill of all {} ranked difficulties complete in {}s", ranked.size(), elapsed / 1000);
        log.info("Running post-backfill rank repair");
        scoreRankingService.reassignAllRanks();
        return CompletableFuture.completedFuture(null);
    }

    public void backfillUser(Long userId) {
        if (userRepository.findByIdAndActiveTrue(userId).isEmpty()) {
            log.warn("Cannot backfill: user {} not found or inactive", userId);
            return;
        }
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED).stream()
                .filter(d -> d.getBlLeaderboardId() != null
                        || (scoreSaberBackfillEnabled && d.getSsLeaderboardId() != null))
                .toList();
        if (ranked.isEmpty()) {
            log.info("No ranked difficulties for user {} backfill", resolvedUserId);
            return;
        }

        log.info("Starting user backfill for {} across {} ranked difficulties", resolvedUserId, ranked.size());
        long start = System.currentTimeMillis();

        Map<String, UUID> modifiers = modifierCacheService.getModifierCodeToId();
        Map<MapDifficulty, Set<Long>> affected = new ConcurrentHashMap<>();

        forEachBounded(ranked, MAX_CONCURRENT_SCORE_FETCHES_PER_USER, difficulty -> {
            try {
                boolean changed = difficulty.getBlLeaderboardId() != null
                        && reconcileUserScore(beatLeader, resolvedUserId, difficulty, modifiers);
                if (!changed && scoreSaberBackfillEnabled && difficulty.getSsLeaderboardId() != null) {
                    changed = reconcileUserScore(scoreSaber, resolvedUserId, difficulty, modifiers);
                }
                if (changed) {
                    affected.put(difficulty, Set.of(resolvedUserId));
                }
            } catch (Exception e) {
                log.error("User backfill failed for user {} difficulty {}: {}",
                        resolvedUserId, difficulty.getId(), e.getMessage());
            }
        });

        log.info("User backfill fetch done for {}: {} affected difficulties. Running recalc...",
                resolvedUserId, affected.size());
        recalculateAfterImport(affected, false);

        long elapsed = System.currentTimeMillis() - start;
        log.info("User backfill complete for {} in {}s", resolvedUserId, elapsed / 1000);
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> backfillUserAsync(Long userId) {
        backfillUser(userId);
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutor")
    public CompletableFuture<Void> backfillUsersAsync(List<Long> userIds) {
        log.info("Starting parallel user backfill for {} users", userIds.size());
        long start = System.currentTimeMillis();

        forEachBounded(userIds, MAX_CONCURRENT_USERS, userId -> {
            try {
                backfillUser(userId);
            } catch (Exception e) {
                log.error("Error backfilling user {}: {}", userId, e.getMessage());
            }
        });

        long elapsed = System.currentTimeMillis() - start;
        log.info("Parallel user backfill complete for {} users in {}s", userIds.size(), elapsed / 1000);
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLegacyCampaignStarted(LegacyCampaignBackfillEvent event) {
        backfillAndSettleLegacyCampaign(event.userId(), event.campaignId());
    }

    public CompletableFuture<Void> recheckLegacyCampaign(UUID campaignId, Long userId) {
        Campaign campaign = campaignRepository.findByIdAndActiveTrue(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        if (!campaign.isLegacy()) {
            throw new ValidationException("Score re-check is only available for legacy campaigns");
        }
        List<Long> targets = userId != null
                ? List.of(duplicateUserService.resolvePrimaryUserId(userId))
                : userCampaignRepository.findUserIdsByCampaignAndStatuses(campaignId, UserCampaignStatus.PARTICIPATING);
        if (targets.isEmpty()) {
            log.info("Legacy campaign re-check for campaign {} has no participants", campaignId);
            return CompletableFuture.completedFuture(null);
        }
        log.info("Legacy campaign re-check queued for campaign {} across {} user(s)", campaignId, targets.size());
        return CompletableFuture.runAsync(() -> runLegacyRecheck(campaignId, targets), backfillExecutor);
    }

    public CompletableFuture<Void> resettleCampaign(UUID campaignId, Long userId) {
        campaignRepository.findByIdAndActiveTrue(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        List<Long> targets = userId != null
                ? List.of(duplicateUserService.resolvePrimaryUserId(userId))
                : userCampaignRepository.findUserIdsByCampaignAndStatuses(campaignId, UserCampaignStatus.PARTICIPATING);
        if (targets.isEmpty()) {
            log.info("Campaign re-settle for campaign {} has no participants", campaignId);
            return CompletableFuture.completedFuture(null);
        }
        log.info("Campaign re-settle queued for campaign {} across {} user(s)", campaignId, targets.size());
        return CompletableFuture.runAsync(() -> runResettle(campaignId, targets), backfillExecutor);
    }

    private void runResettle(UUID campaignId, List<Long> userIds) {
        long start = System.currentTimeMillis();
        for (Long userId : userIds) {
            try {
                campaignEvaluationService.resettleForUser(userId, campaignId);
            } catch (Exception e) {
                log.error("Campaign re-settle failed for user {} campaign {}: {}", userId, campaignId, e.getMessage());
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("Campaign re-settle complete for campaign {} across {} user(s) in {}s",
                campaignId, userIds.size(), elapsed / 1000);
    }

    private void runLegacyRecheck(UUID campaignId, List<Long> userIds) {
        long start = System.currentTimeMillis();
        forEachBounded(userIds, MAX_CONCURRENT_USERS, userId -> {
            try {
                backfillAndSettleLegacyCampaign(userId, campaignId);
            } catch (Exception e) {
                log.error("Legacy campaign re-check failed for user {} campaign {}: {}",
                        userId, campaignId, e.getMessage());
            }
        });
        long elapsed = System.currentTimeMillis() - start;
        log.info("Legacy campaign re-check complete for campaign {} across {} user(s) in {}s",
                campaignId, userIds.size(), elapsed / 1000);
    }

    public void backfillAndSettleLegacyCampaign(Long userId, UUID campaignId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Map<String, UUID> modifiers = modifierCacheService.getModifierCodeToId();
        int recorded = 0;
        for (var node : campaignDifficultyRepository.findActiveWithMapByCampaignId(campaignId)) {
            if (node.isBarrier()) {
                continue;
            }
            MapDifficulty md = node.getMapDifficulty();
            if (md == null || md.getStatus() == MapDifficultyStatus.RANKED) {
                continue;
            }
            if (!scoreRepository.findEligibleCampaignRows(resolved, List.of(md.getId()), Instant.EPOCH).isEmpty()) {
                continue;
            }
            try {
                boolean bl = recordCampaignScore(beatLeader, resolved, md, modifiers);
                boolean ss = recordCampaignScore(scoreSaber, resolved, md, modifiers);
                if (bl || ss) {
                    recorded++;
                }
            } catch (Exception e) {
                log.warn("Campaign backfill failed for user={} difficulty={}: {}", resolved, md.getId(),
                        e.getMessage());
            }
        }
        log.info("Legacy campaign backfill user={} campaign={}: recorded {} platform score(s)", resolved, campaignId,
                recorded);
        campaignEvaluationService.importLegacyScores(resolved, campaignId);
    }

    private <S> boolean recordCampaignScore(PlatformScores<S> platform, Long userId, MapDifficulty md,
            Map<String, UUID> modifiers) {
        String leaderboardId = platform.leaderboardId(md);
        if (leaderboardId == null || leaderboardId.isBlank()) {
            return false;
        }
        S score = platform.fetchPlayerScore(userId, md).orElse(null);
        if (score == null || platform.baseScore(score) == null) {
            return false;
        }
        scoreService.recordCampaignBackfillScore(platform.toRequest(score, md, userId, modifiers));
        return true;
    }

    private <S> boolean reconcileUserScore(PlatformScores<S> platform, Long userId, MapDifficulty difficulty,
            Map<String, UUID> modifiers) {
        S score = platform.fetchPlayerScore(userId, difficulty).orElse(null);
        if (score == null || platform.baseScore(score) == null || platform.banned(score)) {
            return false;
        }

        Optional<Score> existing = scoreRepository
                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(userId, difficulty.getId());
        if (existing.isPresent() && existing.get().getScoreNoMods() > platform.baseScore(score)) {
            return false;
        }

        Double complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
        if (complexity == null) {
            log.warn("No active complexity for difficulty {} - skipping {} user backfill entry",
                    difficulty.getId(), platform.label());
            return false;
        }

        if (!platform.hasPlayerId(score)) {
            platform.stubPlayer(score, userId);
        }

        return importScore(platform, score, new ImportTarget(difficulty, complexity, modifiers), false) != null;
    }

    private void recalculateAfterImport(Map<MapDifficulty, Set<Long>> imported, boolean syncRankWhenSet) {
        Map<MapDifficulty, Set<Long>> changed = imported.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (changed.isEmpty()) {
            log.info("No new scores imported - skipping recalc");
            return;
        }

        Map<UUID, Set<Long>> usersByCategory = new HashMap<>();
        Set<UUID> overallCategoryIds = new HashSet<>();
        Set<Long> allAffectedUsers = new HashSet<>();
        changed.forEach((difficulty, users) -> {
            UUID categoryId = difficulty.getCategory().getId();
            usersByCategory.computeIfAbsent(categoryId, k -> new HashSet<>()).addAll(users);
            allAffectedUsers.addAll(users);
            if (difficulty.getCategory().isCountForOverall()) {
                overallCategoryIds.add(categoryId);
            }
        });

        log.info("Post-import recalc: {} difficulties, {} unique users, {} categories",
                changed.size(), allAffectedUsers.size(), usersByCategory.size());

        fanOut(changed.keySet(), difficulty -> {
            try {
                if (syncRankWhenSet) {
                    scoreRankingService.reassignRanksForBackfill(difficulty.getId());
                } else {
                    scoreRankingService.reassignRanks(difficulty.getId());
                }
                mapDifficultyStatisticsService.recalculate(difficulty, null);
            } catch (Exception ex) {
                log.error("Per-difficulty recalc failed for {}: {}", difficulty.getId(), ex.getMessage());
            }
            return null;
        });

        scoreRecalculationService.recalculatePlayers(usersByCategory, overallCategoryIds, PlayerRecalc.SCORE_IMPORT);

        fanOut(allAffectedUsers, userId -> {
            try {
                campaignEvaluationService.evaluateParticipatingForUser(userId);
            } catch (Exception ex) {
                log.error("Campaign evaluation failed for user {} after import: {}", userId, ex.getMessage());
            }
            return null;
        });

        log.info("Post-import recalc complete: {} users", allAffectedUsers.size());
    }

    @Async("taskExecutor")
    public void startupGapFillAllRankedDifficulties(Instant since) {
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED);
        log.info("Starting parallel gap-fill for {} ranked difficulties since {}", ranked.size(), since);

        long start = System.currentTimeMillis();
        Map<MapDifficulty, Set<Long>> imported = new ConcurrentHashMap<>();

        forEachBounded(ranked, MAX_CONCURRENT_DIFFICULTIES, difficulty -> {
            try {
                Set<Long> users = startupGapFillImport(difficulty, since);
                if (users != null) {
                    imported.put(difficulty, users);
                }
            } catch (Exception e) {
                log.error("Error gap-filling difficulty {}: {}", difficulty.getId(), e.getMessage());
            }
        });

        recalculateAfterImport(imported, false);

        Set<Long> settledUsers = imported.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
        recoverCampaignsForParticipants(settledUsers);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Parallel gap-fill complete for {} difficulties in {}s", ranked.size(), elapsed / 1000);
        log.info("Running post-gap-fill rank repair");
        scoreRankingService.reassignAllRanks();
    }

    private void recoverCampaignsForParticipants(Set<Long> alreadySettled) {
        List<Long> pending = userCampaignRepository
                .findUserIdsByStatusesAndCampaignReleased(UserCampaignStatus.PARTICIPATING, CampaignStatus.DRAFT).stream()
                .filter(userId -> !alreadySettled.contains(userId))
                .toList();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Re-settling campaigns for {} participants not covered by gap-fill imports", pending.size());
        fanOut(pending, userId -> {
            try {
                campaignEvaluationService.evaluateParticipatingForUser(userId);
            } catch (Exception e) {
                log.error("Campaign re-settle failed for user {} during gap-fill recovery: {}",
                        userId, e.getMessage());
            }
            return null;
        });
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
            affectedUserIds.addAll(gapFillFrom(beatLeader, target, since));
        }

        if (scoreSaberBackfillEnabled && difficulty.getSsLeaderboardId() != null) {
            affectedUserIds.addAll(gapFillFrom(scoreSaber, target, since));
        }

        return affectedUserIds;
    }

    public void gapFillDifficulty(MapDifficulty difficulty, Instant since, LeaderboardPlatform platform,
            boolean enrichOnly) {
        ImportTarget target = new ImportTarget(difficulty, null, modifierCacheService.getModifierCodeToId());
        long sinceEpoch = since.getEpochSecond();

        if (platform != LeaderboardPlatform.SCORESABER && difficulty.getBlLeaderboardId() != null) {
            for (BeatLeaderScoreResponse blScore : beatLeaderClient.getRecentScores(
                    difficulty.getBlLeaderboardId(), sinceEpoch)) {
                importScore(beatLeader, blScore, target, enrichOnly);
            }
        }

        if (platform != LeaderboardPlatform.BEATLEADER && scoreSaberBackfillEnabled
                && difficulty.getSsLeaderboardId() != null) {
            importPages(page -> scoreSaber.recentPage(difficulty, page),
                    score -> scoreSaber.cutoff(score, sinceEpoch),
                    batch -> batch.stream().map(score -> importScore(scoreSaber, score, target, enrichOnly)).toList(),
                    0);
        }
    }

    private <S> Set<Long> backfillFrom(PlatformScores<S> platform, String leaderboardId, ImportTarget target) {
        return importPages(platform.rankedPages(target.difficulty(), leaderboardId),
                score -> Cut.KEEP,
                batch -> fanOut(batch, score -> importScore(platform, score, target, false)),
                0);
    }

    private <S> Set<Long> gapFillFrom(PlatformScores<S> platform, ImportTarget target, Instant since) {
        long sinceEpoch = since.getEpochSecond();
        return importPages(page -> platform.recentPage(target.difficulty(), page),
                score -> platform.cutoff(score, sinceEpoch),
                batch -> fanOut(batch, score -> importScore(platform, score, target, false)),
                gapFillPageDelayMs);
    }

    private <S> Set<Long> importPages(IntFunction<ScorePage<S>> fetch, Function<S, Cut> cutoff,
            Function<List<S>, List<Long>> importBatch, long pageDelayMs) {
        Set<Long> affected = new HashSet<>();
        for (int page = 1;; page++) {
            ScorePage<S> fetched = fetch.apply(page);
            if (fetched == null || fetched.scores().isEmpty()) {
                return affected;
            }
            List<S> batch = new ArrayList<>();
            boolean reachedCutoff = false;
            for (S score : fetched.scores()) {
                Cut cut = cutoff.apply(score);
                if (cut == Cut.STOP) {
                    reachedCutoff = true;
                    break;
                }
                if (cut == Cut.KEEP) {
                    batch.add(score);
                }
            }
            importBatch.apply(batch).stream().filter(Objects::nonNull).forEach(affected::add);
            if (reachedCutoff || fetched.last() || !pauseBetweenPages(pageDelayMs)) {
                return affected;
            }
        }
    }

    private static boolean pauseBetweenPages(long delayMs) {
        if (delayMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private <T, R> List<R> fanOut(Collection<T> items, Function<T, R> task) {
        return items.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> task.apply(item), backfillExecutor))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private static <T> void forEachBounded(Collection<T> items, int maxConcurrent, Consumer<T> task) {
        Semaphore semaphore = new Semaphore(maxConcurrent);
        List<Thread> threads = items.stream()
                .map(item -> Thread.startVirtualThread(() -> {
                    semaphore.acquireUninterruptibly();
                    try {
                        task.accept(item);
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
    }

    private <S> Long importScore(PlatformScores<S> platform, S score, ImportTarget target, boolean enrichOnly) {
        MapDifficulty difficulty = target.difficulty();
        try {
            if (platform.banned(score) || !platform.hasPlayerId(score))
                return null;
            Long userId = duplicateUserService.resolvePrimaryUserId(platform.playerId(score));
            Optional<Score> existingScore = scoreRepository
                    .findByUser_IdAndMapDifficulty_IdAndActiveTrue(userId, difficulty.getId());
            if (existingScore.isPresent()
                    && Objects.equals(existingScore.get().getScoreNoMods(), platform.baseScore(score))) {
                platform.mergeIntoExisting(existingScore.get(), score, target, userId);
                return null;
            }
            if (enrichOnly) {
                return null;
            }
            playerImportService.ensurePlayerExists(userId);
            SubmitScoreRequest request = platform.toRequest(score, difficulty, userId, target.modifiers());
            if (target.complexity() != null) {
                scoreService.submitForBackfill(request, difficulty, target.complexity());
            } else {
                scoreService.submit(request);
            }
            return userId;
        } catch (Exception e) {
            log.error("Failed to import {} score {} for difficulty {}: {}",
                    platform.label(), platform.scoreId(score), difficulty.getId(), e.getMessage());
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
        String blHmd = HmdMapper.fromBeatLeaderId(blScore.getHmd());
        if (blHmd != null) {
            score.setHmd(blHmd);
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
                .collect(Collectors.toSet());
        Map<String, UUID> codeToId = modifierCacheService.getModifierCodeToId();
        List<ScoreModifierLink> toAdd = new ArrayList<>();
        for (String code : modifiersStr.split(",")) {
            String trimmed = code.trim();
            if (trimmed.isEmpty())
                continue;
            UUID modifierId = codeToId.get(trimmed);
            if (modifierId == null || existingModifierIds.contains(modifierId))
                continue;
            toAdd.add(ScoreModifierLink.builder()
                    .score(score)
                    .modifier(modifierRepository.getReferenceById(modifierId))
                    .build());
        }
        if (!toAdd.isEmpty()) {
            scoreModifierLinkRepository.saveAll(toAdd);
            log.debug("Added {} missing modifier links to score {}", toAdd.size(), score.getId());
        }
    }

    private ScoreSaberScoreStats fetchScoreSaberStats(ScoreSaberScoreResponse ssScore) {
        if (ssScore.getId() == null || !Boolean.TRUE.equals(ssScore.getHasReplay())) {
            return null;
        }
        return scoreSaberClient.getScoreStats(ssScore.getId()).orElse(null);
    }

    private void enrichExistingScoreSaberScore(Score score, ScoreSaberScoreResponse ssScore, MapDifficulty difficulty,
            Long userId, Map<String, UUID> modifiers) {
        boolean needsStats = score.getStreak115() == null || score.getBombHits() == null;
        ScoreSaberScoreStats stats = needsStats ? fetchScoreSaberStats(ssScore) : null;
        SubmitScoreRequest request = PlatformScoreMapper.fromScoreSaber(ssScore, stats, difficulty.getId(), userId,
                modifiers);
        if (!ScorePayloadFields.merge(score, request)) {
            return;
        }
        score.setMapDifficulty(difficulty);
        scoreRepository.save(score);
        if (needsStats && score.getStreak115() != null) {
            milestoneEvaluationService.evaluateAfterScore(userId, score);
        }
        log.debug("Enriched existing score {} with ScoreSaber data", score.getId());
    }

    private interface PlatformScores<S> {

        String label();

        String leaderboardId(MapDifficulty difficulty);

        Optional<S> fetchPlayerScore(Long userId, MapDifficulty difficulty);

        IntFunction<ScorePage<S>> rankedPages(MapDifficulty difficulty, String leaderboardId);

        ScorePage<S> recentPage(MapDifficulty difficulty, int page);

        Cut cutoff(S score, long sinceEpoch);

        Integer baseScore(S score);

        boolean banned(S score);

        boolean hasPlayerId(S score);

        Long playerId(S score);

        void stubPlayer(S score, Long userId);

        Long scoreId(S score);

        SubmitScoreRequest toRequest(S score, MapDifficulty difficulty, Long userId, Map<String, UUID> modifiers);

        void mergeIntoExisting(Score existing, S score, ImportTarget target, Long userId);
    }

    private final class BeatLeaderScores implements PlatformScores<BeatLeaderScoreResponse> {

        @Override
        public String label() {
            return "BL";
        }

        @Override
        public String leaderboardId(MapDifficulty difficulty) {
            return difficulty.getBlLeaderboardId();
        }

        @Override
        public Optional<BeatLeaderScoreResponse> fetchPlayerScore(Long userId, MapDifficulty difficulty) {
            return beatLeaderClient.getPlayerScoreOnLeaderboard(
                    String.valueOf(userId),
                    difficulty.getMap() != null ? difficulty.getMap().getSongHash() : null,
                    difficulty.getDifficulty() != null ? difficulty.getDifficulty().getDbValue() : null,
                    difficulty.getCharacteristic());
        }

        @Override
        public IntFunction<ScorePage<BeatLeaderScoreResponse>> rankedPages(MapDifficulty difficulty,
                String leaderboardId) {
            return page -> {
                List<BeatLeaderScoreResponse> scores;
                try {
                    scores = beatLeaderClient.getLeaderboardScores(leaderboardId, page, PAGE_SIZE);
                } catch (Exception e) {
                    log.error("BL backfill failed on page {} for difficulty {}: {}",
                            page, difficulty.getId(), e.getMessage());
                    throw new RuntimeException("BL backfill aborted on page " + page, e);
                }
                return new ScorePage<>(scores, scores.size() < PAGE_SIZE);
            };
        }

        @Override
        public ScorePage<BeatLeaderScoreResponse> recentPage(MapDifficulty difficulty, int page) {
            List<BeatLeaderScoreResponse> scores = beatLeaderClient.getLeaderboardScoresSortedByDate(
                    difficulty.getBlLeaderboardId(), page, PAGE_SIZE);
            return new ScorePage<>(scores, scores.size() < PAGE_SIZE);
        }

        @Override
        public Cut cutoff(BeatLeaderScoreResponse score, long sinceEpoch) {
            return score.getTimepost() != null && score.getTimepost() <= sinceEpoch ? Cut.STOP : Cut.KEEP;
        }

        @Override
        public Integer baseScore(BeatLeaderScoreResponse score) {
            return score.getBaseScore();
        }

        @Override
        public boolean banned(BeatLeaderScoreResponse score) {
            return PlatformScoreMapper.hasBannedModifier(score.getModifiers());
        }

        @Override
        public boolean hasPlayerId(BeatLeaderScoreResponse score) {
            return score.getPlayer() != null && score.getPlayer().getId() != null;
        }

        @Override
        public Long playerId(BeatLeaderScoreResponse score) {
            return Long.parseLong(score.getPlayer().getId());
        }

        @Override
        public void stubPlayer(BeatLeaderScoreResponse score, Long userId) {
            BeatLeaderScoreResponse.Player player = new BeatLeaderScoreResponse.Player();
            player.setId(String.valueOf(userId));
            score.setPlayer(player);
        }

        @Override
        public Long scoreId(BeatLeaderScoreResponse score) {
            return score.getId();
        }

        @Override
        public SubmitScoreRequest toRequest(BeatLeaderScoreResponse score, MapDifficulty difficulty, Long userId,
                Map<String, UUID> modifiers) {
            return PlatformScoreMapper.fromBeatLeader(score, difficulty.getId(), userId, modifiers);
        }

        @Override
        public void mergeIntoExisting(Score existing, BeatLeaderScoreResponse score, ImportTarget target,
                Long userId) {
            if (existing.getBlScoreId() != null) {
                reconcileModifierLinks(existing, score.getModifiers());
            } else {
                enrichWithBeatLeaderData(existing, score);
            }
        }
    }

    private final class ScoreSaberScores implements PlatformScores<ScoreSaberScoreResponse> {

        @Override
        public String label() {
            return "SS";
        }

        @Override
        public String leaderboardId(MapDifficulty difficulty) {
            return difficulty.getSsLeaderboardId();
        }

        @Override
        public Optional<ScoreSaberScoreResponse> fetchPlayerScore(Long userId, MapDifficulty difficulty) {
            return scoreSaberClient.getPlayerScoreOnLeaderboard(String.valueOf(userId),
                    difficulty.getSsLeaderboardId());
        }

        @Override
        public IntFunction<ScorePage<ScoreSaberScoreResponse>> rankedPages(MapDifficulty difficulty,
                String leaderboardId) {
            int[] totalPages = { Integer.MAX_VALUE };
            return page -> {
                ScoreSaberScoresPage scoresPage;
                try {
                    scoresPage = scoreSaberClient.getLeaderboardScores(leaderboardId, page);
                } catch (Exception e) {
                    log.error("SS backfill failed on page {} for difficulty {}: {}",
                            page, difficulty.getId(), e.getMessage());
                    throw new RuntimeException("SS backfill aborted on page " + page, e);
                }
                if (scoresPage == null || scoresPage.getData() == null) {
                    return null;
                }
                if (totalPages[0] == Integer.MAX_VALUE && scoresPage.getMetadata() != null
                        && scoresPage.getMetadata().getTotalPages() != null) {
                    totalPages[0] = scoresPage.getMetadata().getTotalPages();
                }
                return new ScorePage<>(scoresPage.getData(), page >= totalPages[0]);
            };
        }

        @Override
        public ScorePage<ScoreSaberScoreResponse> recentPage(MapDifficulty difficulty, int page) {
            ScoreSaberScoresPage scoresPage;
            try {
                scoresPage = scoreSaberClient.getLeaderboardScoresSortedByDate(difficulty.getSsLeaderboardId(), page);
            } catch (Exception e) {
                log.error("SS gap-fill failed on page {} for difficulty {}: {}",
                        page, difficulty.getId(), e.getMessage());
                return null;
            }
            if (scoresPage == null || scoresPage.getData() == null) {
                return null;
            }
            return new ScorePage<>(scoresPage.getData(), scoresPage.getData().size() < PAGE_SIZE);
        }

        @Override
        public Cut cutoff(ScoreSaberScoreResponse score, long sinceEpoch) {
            if (score.getCreatedAt() == null) {
                return Cut.SKIP;
            }
            long ts;
            try {
                ts = Instant.parse(score.getCreatedAt()).getEpochSecond();
            } catch (Exception e) {
                log.warn("Could not parse SS createdAt '{}', skipping", score.getCreatedAt());
                return Cut.SKIP;
            }
            return ts <= sinceEpoch ? Cut.STOP : Cut.KEEP;
        }

        @Override
        public Integer baseScore(ScoreSaberScoreResponse score) {
            return score.getUnmodifiedScore();
        }

        @Override
        public boolean banned(ScoreSaberScoreResponse score) {
            return PlatformScoreMapper.hasBannedModifier(score.getMods());
        }

        @Override
        public boolean hasPlayerId(ScoreSaberScoreResponse score) {
            return score.getPlayer() != null && score.getPlayer().getId() != null;
        }

        @Override
        public Long playerId(ScoreSaberScoreResponse score) {
            return Long.parseLong(score.getPlayer().getId());
        }

        @Override
        public void stubPlayer(ScoreSaberScoreResponse score, Long userId) {
            ScoreSaberScoreResponse.Player player = new ScoreSaberScoreResponse.Player();
            player.setId(String.valueOf(userId));
            score.setPlayer(player);
        }

        @Override
        public Long scoreId(ScoreSaberScoreResponse score) {
            return score.getId();
        }

        @Override
        public SubmitScoreRequest toRequest(ScoreSaberScoreResponse score, MapDifficulty difficulty, Long userId,
                Map<String, UUID> modifiers) {
            return PlatformScoreMapper.fromScoreSaber(score, fetchScoreSaberStats(score), difficulty.getId(), userId,
                    modifiers);
        }

        @Override
        public void mergeIntoExisting(Score existing, ScoreSaberScoreResponse score, ImportTarget target,
                Long userId) {
            enrichExistingScoreSaberScore(existing, score, target.difficulty(), userId, target.modifiers());
        }
    }
}
