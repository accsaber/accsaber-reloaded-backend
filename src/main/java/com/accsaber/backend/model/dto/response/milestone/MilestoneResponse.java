package com.accsaber.backend.model.dto.response.milestone;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.MilestoneQuerySpec;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MilestoneResponse {

    private UUID id;
    private UUID setId;
    private UUID categoryId;
    private String title;
    private String description;
    private String type;
    private String tier;
    private BigDecimal xp;
    private MilestoneQuerySpec querySpec;
    private BigDecimal targetValue;
    private String comparison;
    private boolean blExclusive;
    private String status;
    private BigDecimal completionPercentage;
    private Long completions;
    private Long totalPlayers;
    private Instant createdAt;
}
