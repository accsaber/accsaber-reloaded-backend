package com.accsaber.backend.model.dto.response.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.CriteriaStatus;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublicMapDifficultyResponse {

    UUID id;
    UUID mapId;
    String songName;
    String songSubName;
    String songAuthor;
    String mapAuthor;
    String coverUrl;
    String beatsaverCode;
    UUID categoryId;
    Difficulty difficulty;
    String characteristic;
    MapDifficultyStatus status;
    String ssLeaderboardId;
    String blLeaderboardId;
    Integer maxScore;
    Instant rankedAt;
    Instant createdAt;
    BigDecimal complexity;
    Integer rankUpvotes;
    Integer rankDownvotes;
    CriteriaStatus criteriaStatus;
    MapDifficultyStatisticsResponse statistics;
}
