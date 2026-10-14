package com.accsaber.backend.model.dto.request.campaign;

import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignModifierRequirement;

import jakarta.validation.constraints.NotNull;

public record CampaignModifierRequirementRequest(
        @NotNull UUID modifierId,
        @NotNull CampaignModifierRequirement requirement) {
}
