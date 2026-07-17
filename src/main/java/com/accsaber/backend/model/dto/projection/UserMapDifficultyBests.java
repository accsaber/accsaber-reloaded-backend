package com.accsaber.backend.model.dto.projection;

import java.math.BigDecimal;
import java.util.UUID;

public record UserMapDifficultyBests(
        UUID mapDifficultyId,
        Integer maxScore,
        Integer bestScore,
        Integer bestScoreNoMods,
        BigDecimal bestAp,
        Integer bestStreak115,
        Integer bestRank,
        Integer fcFlag,
        Integer noNfFlag) {

    public boolean hasFullCombo() {
        return fcFlag != null && fcFlag == 1;
    }

    public boolean hasNoNfPass() {
        return noNfFlag != null && noNfFlag == 1;
    }
}
