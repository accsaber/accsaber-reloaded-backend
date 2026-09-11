package com.accsaber.backend.model.dto.projection;

import java.time.Instant;
import java.util.UUID;

public interface EstimateSummaryRow {

    UUID getMapDifficultyId();

    double getComplexity();

    String getVersion();

    Instant getUpdatedAt();

    String getModelHash();
}
