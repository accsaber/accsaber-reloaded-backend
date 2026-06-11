package com.accsaber.backend.model.dto.request.campaign;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;

import lombok.Data;

@Data
public class UpdateCampaignRequest {

    private String name;
    private String slug;
    private String summary;
    private String description;
    private Boolean progressionAgnostic;
    private CampaignCompletionMode completionMode;
    private Boolean playlistExportEnabled;
    private BigDecimal completionXp;
    private String creatorAlias;
    private Boolean seekingCuration;
    private String backgroundUrl;
    private String iconUrl;
    private List<UUID> tagIds;
}
