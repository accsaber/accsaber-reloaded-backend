package com.accsaber.backend.model.dto.response.milestone;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserMilestoneProgressResponse {

    private UUID milestoneId;
    private String title;
    private String description;
    private String type;
    private String tier;
    private BigDecimal xp;
    private BigDecimal targetValue;
    private BigDecimal progress;
    private BigDecimal normalizedProgress;
    private boolean completed;
    private Instant completedAt;
    private BigDecimal completionPercentage;
    private UUID setId;
    private UUID categoryId;
}
