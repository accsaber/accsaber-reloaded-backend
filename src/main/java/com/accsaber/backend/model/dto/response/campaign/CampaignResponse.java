package com.accsaber.backend.model.dto.response.campaign;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignBackgroundPlacement;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.dto.response.staff.PublicStaffUserResponse;
import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignResponse {

    private UUID id;
    private String creatorId;
    private String creatorName;
    private String creatorAlias;
    private String name;
    private String slug;
    private String summary;
    private String description;
    private CampaignStatus status;
    private boolean official;
    private boolean progressionAgnostic;
    private CampaignCompletionMode completionMode;
    private boolean legacy;
    private Double completionXp;
    private boolean playlistExportEnabled;
    private String backgroundUrl;
    private String backgroundColor;
    private CampaignBackgroundPlacement background;
    private String iconUrl;
    private int difficultyCount;
    private int totalUpvotes;
    private int totalDownvotes;
    private double voteScore;
    private CampaignVoteDirection myVote;
    private UserCampaignStatus myProgressStatus;
    private Integer myCompletedDifficulties;
    private List<CampaignTagResponse> tags;
    private List<CampaignItemAwardResponse> completionItems;
    private Double totalXp;
    private Integer totalRewardCount;
    private List<CampaignItemAwardResponse> rewards;
    private boolean loved;
    private Instant lovedAt;
    private PublicStaffUserResponse lovedBy;
    private Instant curatedAt;
    private PublicStaffUserResponse curatedBy;
    private Instant publishedAt;
    private Instant createdAt;
}
