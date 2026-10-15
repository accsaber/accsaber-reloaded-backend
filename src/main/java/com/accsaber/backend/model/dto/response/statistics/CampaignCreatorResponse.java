package com.accsaber.backend.model.dto.response.statistics;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignCreatorResponse {

    @JsonUnwrapped
    private UserIdentity user;
    private long campaigns;
    private long curatedCampaigns;
    private long participants;
    private long completions;
    private Double completionRate;
}
