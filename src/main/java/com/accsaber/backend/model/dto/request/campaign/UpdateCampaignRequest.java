package com.accsaber.backend.model.dto.request.campaign;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCampaignRequest {

    @Size(max = 100)
    private String name;
    @Size(max = 80)
    private String slug;
    @Size(max = 500)
    private String summary;
    @Size(max = 4000)
    private String description;
    private Boolean progressionAgnostic;
    private CampaignCompletionMode completionMode;
    private Boolean playlistExportEnabled;
    private BigDecimal completionXp;
    @Size(max = 64)
    private String creatorAlias;
    private Boolean seekingCuration;
    @Size(max = 512)
    private String backgroundUrl;
    @Size(max = 512)
    private String iconUrl;
    @Size(max = 10)
    private List<UUID> tagIds;
}
