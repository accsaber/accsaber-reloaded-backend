package com.accsaber.backend.service.score;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.PlayerImportService;
import com.accsaber.backend.service.score.PlatformScores.Cut;
import com.accsaber.backend.service.score.PlatformScores.ImportTarget;
import com.accsaber.backend.service.score.PlatformScores.ScorePage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
class ScoreImporter {

    private final BeatLeaderScores beatLeader;
    private final ScoreSaberScores scoreSaber;
    private final ScoreService scoreService;
    private final PlayerImportService playerImportService;
    private final ScoreRepository scoreRepository;
    private final DuplicateUserService duplicateUserService;
    @Qualifier("backfillExecutor")
    private final Executor backfillExecutor;

    @Value("${accsaber.backfill.scoresaber-enabled:true}")
    private boolean scoreSaberBackfillEnabled = true;

    BeatLeaderScores beatLeader() {
        return beatLeader;
    }

    ScoreSaberScores scoreSaber() {
        return scoreSaber;
    }

    boolean scoreSaberEnabled() {
        return scoreSaberBackfillEnabled;
    }

    <S> Set<Long> backfillFrom(PlatformScores<S> platform, String leaderboardId, ImportTarget target) {
        return importPages(platform.rankedPages(target.difficulty(), leaderboardId),
                score -> Cut.KEEP,
                batch -> ImportConcurrency.fanOut(batch, backfillExecutor,
                        score -> importScore(platform, score, target, false)),
                0);
    }

    <S> Set<Long> gapFillFrom(PlatformScores<S> platform, ImportTarget target, Instant since, long pageDelayMs) {
        long sinceEpoch = since.getEpochSecond();
        return importPages(page -> platform.recentPage(target.difficulty(), page),
                score -> platform.cutoff(score, sinceEpoch),
                batch -> ImportConcurrency.fanOut(batch, backfillExecutor,
                        score -> importScore(platform, score, target, false)),
                pageDelayMs);
    }

    <S> Set<Long> importPages(IntFunction<ScorePage<S>> fetch, Function<S, Cut> cutoff,
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

    <S> Long importScore(PlatformScores<S> platform, S score, ImportTarget target, boolean enrichOnly) {
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

    <S> boolean recordCampaignScore(PlatformScores<S> platform, Long userId, MapDifficulty md,
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
}
