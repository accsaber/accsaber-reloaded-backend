package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignResponse {

    private UUID id;
    private Long creatorId;
    private String creatorName;
    private String creatorAlias;
    private String name;
    private String slug;
    private String summary;
    private String description;
    private CampaignStatus status;
    private boolean seekingCuration;
    private boolean official;
    private boolean progressionAgnostic;
    private CampaignCompletionMode completionMode;
    private boolean legacy;
    private BigDecimal completionXp;
    private boolean playlistExportEnabled;
    private String backgroundUrl;
    private String backgroundColor;
    private String iconUrl;
    private int difficultyCount;
    private int totalUpvotes;
    private int totalDownvotes;
    private double voteScore;
    private CampaignVoteDirection myVote;
    private List<CampaignTagResponse> tags;
    private Instant submittedAt;
    private Instant curatedAt;
    private Instant publishedAt;
    private Instant createdAt;
}
