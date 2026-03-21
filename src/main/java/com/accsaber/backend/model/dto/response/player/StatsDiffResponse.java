package com.accsaber.backend.model.dto.response.player;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StatsDiffResponse {

    private UUID categoryId;
    private BigDecimal apDiff;
    private BigDecimal scoreXpDiff;
    private BigDecimal milestoneXpDiff;
    private BigDecimal averageAccDiff;
    private BigDecimal averageApDiff;
    private Integer rankingDiff;
    private Integer countryRankingDiff;
    private Integer rankedPlaysDiff;
    private Instant from;
    private Instant to;
}
