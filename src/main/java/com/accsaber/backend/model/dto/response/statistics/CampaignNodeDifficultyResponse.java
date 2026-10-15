package com.accsaber.backend.model.dto.response.statistics;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignNodeDifficultyResponse {

    private UUID campaignDifficultyId;
    private MapDifficultyRef map;
    private boolean barrier;
    private boolean terminal;
    private CampaignRequirementType requirementType;
    private Double requirementValue;
    private double xp;
    private long unlocked;
    private long cleared;
    private Double clearRate;
    private Double medianDaysToClear;
}
