package com.accsaber.backend.model.dto.response.statistics;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignCompletorResponse {

    @JsonUnwrapped
    private UserIdentity user;
    private long completed;
    private long inProgress;
    private long nodesCleared;
    private double campaignXp;
}
