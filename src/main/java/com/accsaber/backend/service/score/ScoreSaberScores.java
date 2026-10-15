package com.accsaber.backend.service.score;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;

import org.springframework.stereotype.Component;

import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreStats;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoresPage;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.util.PlatformScoreMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
class ScoreSaberScores implements PlatformScores<ScoreSaberScoreResponse> {

    private final ScoreSaberClient scoreSaberClient;
    private final ScoreRepository scoreRepository;
    private final MilestoneEvaluationService milestoneEvaluationService;

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
        return PlatformScoreMapper.fromScoreSaber(score, fetchStats(score), difficulty.getId(), userId,
                modifiers);
    }

    @Override
    public void mergeIntoExisting(Score existing, ScoreSaberScoreResponse score, ImportTarget target,
            Long userId) {
        enrichExisting(existing, score, target.difficulty(), userId, target.modifiers());
    }

    private ScoreSaberScoreStats fetchStats(ScoreSaberScoreResponse ssScore) {
        if (ssScore.getId() == null || !Boolean.TRUE.equals(ssScore.getHasReplay())) {
            return null;
        }
        return scoreSaberClient.getScoreStats(ssScore.getId()).orElse(null);
    }

    private void enrichExisting(Score score, ScoreSaberScoreResponse ssScore, MapDifficulty difficulty,
            Long userId, Map<String, UUID> modifiers) {
        boolean needsStats = score.getStreak115() == null || score.getBombHits() == null;
        ScoreSaberScoreStats stats = needsStats ? fetchStats(ssScore) : null;
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
}
