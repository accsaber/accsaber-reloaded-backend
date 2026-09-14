package com.accsaber.backend.model.dto.response.player;

import java.time.Instant;
import java.util.List;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
public class UserResponse {

    String id;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String blId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String ssId;

    String name;
    String avatarUrl;
    String cdnAvatarUrl;
    PublicClanResponse clan;
    String country;
    String bio;
    Double totalXp;
    Double totalScoreXp;
    Double totalMilestoneXp;
    Double totalMilestoneSetBonusXp;
    Integer xpRanking;
    Integer xpCountryRanking;
    UserLevelData levelData;
    boolean banned;
    boolean playerInactive;
    String hmd;
    Instant lastActiveTime;
    Instant createdAt;

    @With
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<UserCategoryStatisticsResponse> statistics;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    UserRelationCounts relations;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String supporterTier;
}
