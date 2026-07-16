package com.accsaber.backend.model.dto.response.campaign;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignConnectionResponse {

    private UUID comesFromCampaignDifficultyId;
    private String color;
}
