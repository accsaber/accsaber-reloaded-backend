package com.accsaber.backend.model.dto.request.campaign;

import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCampaignRequest {

    private Long creatorId;

    private String creatorAlias;

    @NotBlank
    private String name;

    private String slug;

    private String summary;

    private String description;

    private Boolean progressionAgnostic;

    private CampaignCompletionMode completionMode;

    private Boolean playlistExportEnabled;

    private String backgroundUrl;

    private List<UUID> tagIds;
}
