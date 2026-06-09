package com.accsaber.backend.model.dto.request.campaign;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;

import lombok.Data;

@Data
public class UpdateCampaignDifficultyRequest {

    private CampaignRequirementType requirementType;
    private CampaignPrerequisiteMode prerequisiteMode;
    private BigDecimal requirementValue;
    private String description;
    private String checkpointLabel;
    private String checkpointAvatarUrl;
    private String checkpointColor;
    private String borderColor;
    private String borderShape;
    private String size;
    private String checkpointSize;
    private Integer positionX;
    private Integer positionY;
    private BigDecimal xp;
    private List<UUID> prerequisiteCampaignDifficultyIds;
}
