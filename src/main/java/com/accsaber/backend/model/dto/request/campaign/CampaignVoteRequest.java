package com.accsaber.backend.model.dto.request.campaign;

import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;

import jakarta.validation.constraints.NotNull;

public record CampaignVoteRequest(
        @NotNull CampaignVoteDirection direction) {
}
