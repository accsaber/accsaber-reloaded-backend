package com.accsaber.backend.model.dto.response.statistics;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignCreatorResponse {

    private PlayerRef player;
    private long campaigns;
    private long curatedCampaigns;
    private long participants;
    private long completions;
    private Double completionRate;
}
