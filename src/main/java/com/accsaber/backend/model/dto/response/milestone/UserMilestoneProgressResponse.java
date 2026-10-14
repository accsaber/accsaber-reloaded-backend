package com.accsaber.backend.model.dto.response.milestone;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.MilestoneQuerySpec;

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

    private String iconGroup;
    private MilestoneQuerySpec querySpec;
    private Double xp;
    private Double targetValue;
    private Double progress;
    private Double normalizedProgress;
    private boolean completed;
    private Instant completedAt;
    private Double completionPercentage;
    private UUID setId;
    private UUID categoryId;

    private double positionX;

    private double positionY;

    private List<MilestoneRewardResponse> rewards;
}
