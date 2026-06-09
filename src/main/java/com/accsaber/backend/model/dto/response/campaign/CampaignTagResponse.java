package com.accsaber.backend.model.dto.response.campaign;

import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignTagKind;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignTagResponse {

    private UUID id;
    private CampaignTagKind kind;
    private String name;
    private UUID categoryId;
    private boolean system;
}
