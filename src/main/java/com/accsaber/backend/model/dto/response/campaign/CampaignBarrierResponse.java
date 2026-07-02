package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.CampaignLabelPosition;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignBarrierResponse {

    private UUID id;
    private BarrierConditionType conditionType;
    private BigDecimal conditionValue;
    private String description;
    private String checkpointLabel;
    private CampaignLabelPosition checkpointLabelPosition;
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
    private List<UUID> affectedCampaignDifficultyIds;
    private List<CampaignItemAwardResponse> items;
}
