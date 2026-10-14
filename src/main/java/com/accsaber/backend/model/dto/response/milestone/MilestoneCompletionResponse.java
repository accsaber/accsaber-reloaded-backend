package com.accsaber.backend.model.dto.response.milestone;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.entity.map.Difficulty;
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
    private String coverUrl;
    private String cdnCoverUrl;
    private Difficulty difficulty;
    private String songName;
    private String songSubName;
    private String songAuthor;
    private String mapAuthor;
}
