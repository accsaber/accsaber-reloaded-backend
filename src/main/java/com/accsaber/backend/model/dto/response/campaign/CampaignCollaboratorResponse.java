package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;
import java.util.UUID;

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
    private Long userId;
    private String userName;
    private String userAvatarUrl;
    private String userCdnAvatarUrl;
    private String userCountry;
    private CampaignCollaboratorStatus status;
    private Long invitedById;
    private Instant createdAt;
}
