package com.accsaber.backend.model.dto.response.milestone;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MilestoneCompletionResponse {

    private UUID milestoneId;
    private String title;
    private String description;
    private String type;
    private String tier;
    private BigDecimal xp;
    private BigDecimal targetValue;
    private String comparison;
    private boolean blExclusive;
    private UUID setId;
    private UUID categoryId;
    private Long completions;
    private Long totalPlayers;
    private BigDecimal completionPercentage;
    private Boolean userCompleted;
    private Instant userCompletedAt;
    private UUID achievedWithScoreId;
    private Integer score;
    private Integer maxScore;
    private String coverUrl;
    private String difficulty;
    private String songName;
    private String songAuthor;
    private String mapAuthor;
}
