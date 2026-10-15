package com.accsaber.backend.model.dto.response.milestone;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;
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

    private String iconGroup;
    private MilestoneQuerySpec querySpec;
    private Double xp;
    private Double targetValue;
    private String comparison;
    private UUID setId;
    private Double positionX;
    private Double positionY;
    private List<MilestoneRewardResponse> rewards;
    private UUID categoryId;
    private Long completions;
    private Long totalPlayers;
    private Double completionPercentage;
    private Double userProgress;
    private Double userNormalizedProgress;
    private Boolean userCompleted;
    private Instant userCompletedAt;
    private UUID achievedWithScoreId;
    private Integer score;
    private Integer maxScore;
    private MapDifficultyRef map;
}
