package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;
import java.time.Instant;

import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignLeaderboardEntry {

    private Integer rank;
    private CampaignLeaderboardPlayer player;
    private Instant completedAt;
    private BigDecimal averageAccuracy;
    private BigDecimal averageAp;
    private Integer nodesCounted;
    private UserCampaignStatus progressStatus;
    private Integer completedNodes;
    private Integer totalNodes;
}
