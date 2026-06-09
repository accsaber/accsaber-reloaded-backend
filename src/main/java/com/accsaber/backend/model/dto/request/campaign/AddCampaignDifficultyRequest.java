package com.accsaber.backend.model.dto.request.campaign;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddCampaignDifficultyRequest {

    @NotNull
    private UUID mapDifficultyId;

    @NotNull
    private CampaignRequirementType requirementType;

    @NotNull
    private BigDecimal requirementValue;

    private String description;

    private String checkpointLabel;

    private String checkpointAvatarUrl;

    private String checkpointColor;

    private String borderColor;

    private String borderShape;

    private String size;

    private String checkpointSize;

    @NotNull
    private Integer positionX;

    @NotNull
    private Integer positionY;

    private BigDecimal xp;

    private List<UUID> prerequisiteCampaignDifficultyIds;

    private CampaignPrerequisiteMode prerequisiteMode;
}
