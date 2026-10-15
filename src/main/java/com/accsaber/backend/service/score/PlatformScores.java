package com.accsaber.backend.service.score;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;

import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;

interface PlatformScores<S> {

    int PAGE_SIZE = 100;

    enum Cut {
        KEEP, SKIP, STOP
    }

    record ScorePage<S>(List<S> scores, boolean last) {
    }

    record ImportTarget(MapDifficulty difficulty, Double complexity, Map<String, UUID> modifiers) {
    }

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
