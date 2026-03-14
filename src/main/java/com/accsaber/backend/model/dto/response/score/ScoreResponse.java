package com.accsaber.backend.model.dto.response.score;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScoreResponse {

    private UUID id;
    private Long userId;
    private UUID mapDifficultyId;
    private Integer score;
    private Integer scoreNoMods;
    private BigDecimal accuracy;
    private Integer rank;
    private Integer rankWhenSet;
    private BigDecimal ap;
    private BigDecimal weightedAp;
    private Long blScoreId;
    private Integer maxCombo;
    private Integer badCuts;
    private Integer misses;
    private Integer wallHits;
    private Integer bombHits;
    private Integer pauses;
    private Integer streak115;
    private Integer playCount;
    private String hmd;
    private Instant timeSet;
    private boolean reweightDerivative;
    private BigDecimal xpGained;
    private List<UUID> modifierIds;
    private Instant createdAt;
}
