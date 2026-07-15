package com.accsaber.backend.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventMissionTargets(
        UUID categoryId,
        UUID mapDifficultyId,
        String playerId,
        BigDecimal acc,
        BigDecimal ap,
        Integer score,
        Integer count,
        Integer xp,
        BigDecimal thresholdAp,
        Integer streak,
        Instant rankedBefore,
        Boolean curatedOnly) {

    public Long playerIdAsLong() {
        return playerId == null ? null : Long.valueOf(playerId);
    }
}
