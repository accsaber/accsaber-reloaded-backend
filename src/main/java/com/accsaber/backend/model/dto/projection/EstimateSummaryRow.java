package com.accsaber.backend.model.dto.projection;

import java.time.Instant;
import java.util.UUID;

public record EstimateSummaryRow(UUID mapDifficultyId, double complexity, String version, Instant updatedAt,
        String modelHash) {
}
