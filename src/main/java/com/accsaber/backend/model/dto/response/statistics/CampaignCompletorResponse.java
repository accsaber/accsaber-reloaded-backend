package com.accsaber.backend.model.dto.response.statistics;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignCompletorResponse {

    private PlayerRef player;
    private long completed;
    private long inProgress;
    private long nodesCleared;
    private double campaignXp;
}
