package com.accsaber.backend.model.dto.response.player;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserCategoryStatisticsResponse {

    private UUID id;
    private String userId;
    private UUID categoryId;
    private Integer ranking;
    private Integer countryRanking;
    private BigDecimal ap;
    private BigDecimal scoreXp;
    private BigDecimal averageAcc;
    private BigDecimal averageAp;
    private Integer rankedPlays;
    private UUID topPlayId;
    private Instant createdAt;
    private Instant updatedAt;
}
