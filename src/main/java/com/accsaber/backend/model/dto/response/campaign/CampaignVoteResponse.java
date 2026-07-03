package com.accsaber.backend.model.dto.response.campaign;

import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignVoteResponse {

    private UUID campaignId;
    private int totalUpvotes;
    private int totalDownvotes;
    private double voteScore;
    private CampaignVoteDirection myVote;
}
