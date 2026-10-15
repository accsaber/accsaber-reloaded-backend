package com.accsaber.backend.service.score;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.score.PlatformScores.ImportTarget;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserScoreBackfillService {

    private static final int MAX_CONCURRENT_SCORE_FETCHES_PER_USER = 20;
    private static final int MAX_CONCURRENT_USERS = 2;

    private final ScoreImporter importer;
    private final ScoreImportRecalculator recalculator;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyComplexityService mapComplexityService;
    private final ModifierCacheService modifierCacheService;
    private final ScoreRepository scoreRepository;

    public void backfillUser(Long userId) {
        if (userRepository.findByIdAndActiveTrue(userId).isEmpty()) {
            log.warn("Cannot backfill: user {} not found or inactive", userId);
            return;
        }
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        List<MapDifficulty> ranked = mapDifficultyRepository
                .findByStatusAndActiveTrueWithCategory(MapDifficultyStatus.RANKED).stream()
                .filter(d -> d.getBlLeaderboardId() != null
                        || (importer.scoreSaberEnabled() && d.getSsLeaderboardId() != null))
                .toList();
        if (ranked.isEmpty()) {
            log.info("No ranked difficulties for user {} backfill", resolvedUserId);
            return;
        }

        log.info("Starting user backfill for {} across {} ranked difficulties", resolvedUserId, ranked.size());
        long start = System.currentTimeMillis();

        Map<String, UUID> modifiers = modifierCacheService.getModifierCodeToId();
        Map<MapDifficulty, Set<Long>> affected = new ConcurrentHashMap<>();

        ImportConcurrency.forEachBounded(ranked, MAX_CONCURRENT_SCORE_FETCHES_PER_USER, difficulty -> {
            try {
                boolean changed = difficulty.getBlLeaderboardId() != null
                        && reconcileUserScore(importer.beatLeader(), resolvedUserId, difficulty, modifiers);
                if (!changed && importer.scoreSaberEnabled() && difficulty.getSsLeaderboardId() != null) {
                    changed = reconcileUserScore(importer.scoreSaber(), resolvedUserId, difficulty, modifiers);
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
        recalculator.recalculateAfterImport(affected, false);

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

        ImportConcurrency.forEachBounded(userIds, MAX_CONCURRENT_USERS, userId -> {
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

        return importer.importScore(platform, score, new ImportTarget(difficulty, complexity, modifiers),
                false) != null;
    }
}
