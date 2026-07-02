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
    private CampaignResponse campaign;
    private UserCampaignStatus progressStatus;
    private Instant startedAt;
    private Instant completedAt;
    private int completedDifficulties;
}
