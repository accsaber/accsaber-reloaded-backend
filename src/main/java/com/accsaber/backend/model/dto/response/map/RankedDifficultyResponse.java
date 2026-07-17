package com.accsaber.backend.model.dto.response.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RankedDifficultyResponse {
    UUID id;
    String songHash;
    String songName;
    Difficulty difficulty;
    BigDecimal complexity;
    String categoryCode;
    String ssLeaderboardId;
    String blLeaderboardId;
    Instant rankedAt;
}
