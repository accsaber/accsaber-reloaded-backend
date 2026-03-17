package com.accsaber.backend.model.dto.response.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MapDifficultyStatisticsResponse {

    UUID id;
    BigDecimal maxAp;
    BigDecimal minAp;
    BigDecimal averageAp;
    int totalScores;
    TopScoreSnapshot topScore;
    Instant createdAt;
}
