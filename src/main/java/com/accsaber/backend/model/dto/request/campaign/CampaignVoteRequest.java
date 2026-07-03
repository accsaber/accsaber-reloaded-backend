package com.accsaber.backend.model.dto.request.campaign;

import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CampaignVoteRequest {

    @NotNull
    private CampaignVoteDirection direction;
}
