package com.accsaber.backend.model.dto.response.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TopScoreSnapshot {

    UUID scoreId;
    String userId;
    String userName;
    String avatarUrl;
    Integer score;
    BigDecimal accuracy;
    BigDecimal ap;
    Instant timeSet;
}
