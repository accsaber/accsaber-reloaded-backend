package com.accsaber.backend.model.dto.projection;

import java.util.UUID;

public record SimulationScoreRow(Long userId, UUID mapDifficultyId, UUID categoryId, Integer score,
        Integer maxScore, double ap) {
}
