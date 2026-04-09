package com.accsaber.backend.model.dto.response.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.CriteriaStatus;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.map.VoteType;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MapDifficultyResponse {

    UUID id;
    UUID mapId;
    String songName;
    String songSubName;
    String songAuthor;
    String mapAuthor;
    String coverUrl;
    UUID categoryId;
    UUID previousVersionId;
    Difficulty difficulty;
    String characteristic;
    boolean active;
    MapDifficultyStatus status;
    CriteriaStatus criteriaStatus;
    String ssLeaderboardId;
    String blLeaderboardId;
    Integer maxScore;
    BigDecimal complexity;
    Instant rankedAt;
    Instant createdAt;
    UUID createdBy;
    String createdByUsername;
    String createdByAvatarUrl;
    UUID lastUpdatedBy;
    String lastUpdatedByUsername;
    int rankUpvotes;
    int rankDownvotes;
    int criteriaUpvotes;
    int criteriaDownvotes;
    VoteType headCriteriaVote;
    MapDifficultyStatisticsResponse statistics;
}
