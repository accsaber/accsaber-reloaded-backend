package com.accsaber.backend.model.dto.response.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MapComplexityHistoryResponse {

    UUID id;
    UUID mapDifficultyId;
    Difficulty difficulty;
    String characteristic;
    BigDecimal complexity;
    String reason;
    boolean active;
    UUID supersedesId;
    Instant createdAt;
}
