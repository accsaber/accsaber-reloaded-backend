package com.accsaber.backend.model.dto.response.score;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MyScoreSummary {

    private UUID id;
    private Integer score;
    private BigDecimal accuracy;
    private BigDecimal ap;
    private BigDecimal weightedAp;
    private Integer rank;
    private Instant timeSet;
}
