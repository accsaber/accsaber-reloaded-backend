package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignDifficultyResponse {

    private UUID id;
    private UUID mapDifficultyId;
    private String songName;
    private String songAuthor;
    private String mapAuthor;
    private String coverUrl;
    private String difficulty;
    private String characteristic;
    private CampaignRequirementType requirementType;
    private BigDecimal requirementValue;
    private CampaignPrerequisiteMode prerequisiteMode;
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
    private List<CampaignItemAwardResponse> items;
}
