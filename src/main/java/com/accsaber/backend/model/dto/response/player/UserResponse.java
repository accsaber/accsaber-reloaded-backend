package com.accsaber.backend.model.dto.response.player;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
public class UserResponse {

    String id;
    String name;
    String avatarUrl;
    String country;
    BigDecimal totalXp;
    BigDecimal totalScoreXp;
    BigDecimal totalMilestoneXp;
    BigDecimal totalMilestoneSetBonusXp;
    Integer xpRanking;
    Integer xpCountryRanking;
    boolean banned;
    Instant createdAt;

    @With
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<UserCategoryStatisticsResponse> statistics;
}
