package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignDifficultyProgressResponse {

    private UUID campaignDifficultyId;
    private UUID mapDifficultyId;
    private String songName;
    private String difficulty;
    private String characteristic;
    private String checkpointLabel;
    private String checkpointAvatarUrl;
    private String checkpointColor;
    private String borderColor;
    private String borderShape;
    private String size;
    private String checkpointSize;
    private CampaignRequirementType requirementType;
    private BigDecimal requirementValue;
    private BigDecimal userValue;
    private Integer userScore;
    private boolean completed;
    private boolean unlocked;
}
