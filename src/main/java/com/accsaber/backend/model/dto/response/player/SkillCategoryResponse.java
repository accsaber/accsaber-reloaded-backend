package com.accsaber.backend.model.dto.response.player;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SkillCategoryResponse {

    String categoryCode;
    String categoryName;
    double skillLevel;
    SkillComponents components;

    @Value
    @Builder
    public static class SkillComponents {
        double rank;
        double sustained;
        double peak;
        double combined;
        BigDecimal rawApForOneGain;
        BigDecimal topAp;
        Integer categoryRank;
        Long activePlayers;
    }
}
