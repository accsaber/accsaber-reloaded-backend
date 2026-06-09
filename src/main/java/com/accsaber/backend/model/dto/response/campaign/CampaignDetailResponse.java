package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignDetailResponse {

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
    private boolean progressionAgnostic;
    private CampaignCompletionMode completionMode;
    private boolean legacy;
    private BigDecimal completionXp;
    private String curatorNotes;
    private boolean playlistExportEnabled;
    private String backgroundUrl;
    private Instant submittedAt;
    private Instant curatedAt;
    private Instant createdAt;
    private List<CampaignTagResponse> tags;
    private List<CampaignDifficultyResponse> difficulties;
    private List<CampaignItemAwardResponse> completionItems;
}
