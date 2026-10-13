package com.accsaber.backend.model.dto.request.campaign;

import java.util.Collection;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;

import lombok.Builder;

@Builder
public record CampaignFilter(
        Collection<CampaignStatus> status,
        Collection<UUID> tagIds,
        Long creatorId,
        Long memberId,
        String search,
        Boolean official,
        Boolean loved,
        Long participantId,
        Collection<UserCampaignStatus> progressStatus) {
}
