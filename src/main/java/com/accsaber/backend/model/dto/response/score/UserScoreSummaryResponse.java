package com.accsaber.backend.model.dto.response.score;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserScoreSummaryResponse {
    UUID mapDifficultyId;
    String songHash;
    String ssLeaderboardId;
    String blLeaderboardId;
    BigDecimal ap;
    BigDecimal accuracy;
    Integer score;
    Integer maxScore;
    Integer rank;
    Long blScoreId;
    Long ssScoreId;
    Instant timeSet;
}
