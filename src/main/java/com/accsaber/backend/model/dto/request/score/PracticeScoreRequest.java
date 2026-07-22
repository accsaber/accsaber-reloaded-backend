package com.accsaber.backend.model.dto.request.score;

import java.time.Instant;
import java.util.UUID;

public record PracticeScoreRequest(
        UUID id,
        String name,
        int score,
        int level,
        double accuracy,
        int badCuts,
        int bombHits,
        Instant playedAt) {
}
