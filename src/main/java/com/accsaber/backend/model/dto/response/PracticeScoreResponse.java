package com.accsaber.backend.model.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PracticeScoreResponse {
    UUID id;
    String name;
    int score;
    int level;
    double accuracy;
    int badCuts;
    int bombHits;
    Instant playedAt;
}
