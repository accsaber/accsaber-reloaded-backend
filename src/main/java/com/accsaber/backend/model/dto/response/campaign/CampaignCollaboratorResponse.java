package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignCollaboratorResponse {

    private UUID id;
    private UUID campaignId;
    private String campaignName;
    private String campaignSlug;
    private PlayerRef user;
    private CampaignCollaboratorStatus status;
    private PlayerRef invitedBy;
    private Instant createdAt;
}
