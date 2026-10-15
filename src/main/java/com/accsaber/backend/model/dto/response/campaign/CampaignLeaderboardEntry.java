package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignLeaderboardEntry {

    private Integer rank;
    private PlayerRef player;
    private Instant completedAt;
    private Double averageAccuracy;
    private Double averageAp;
    private Integer nodesCounted;
    private UserCampaignStatus progressStatus;
    private Integer completedNodes;
    private Integer totalNodes;
}
