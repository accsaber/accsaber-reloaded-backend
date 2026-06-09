package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserCampaignResponse {

    private UUID id;
    private UUID campaignId;
    private String campaignName;
    private String campaignSlug;
    private UserCampaignStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Integer totalDifficulties;
    private Integer completedDifficulties;
}
