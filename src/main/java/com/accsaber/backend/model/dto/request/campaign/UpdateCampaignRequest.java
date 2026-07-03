package com.accsaber.backend.model.dto.request.campaign;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.validation.CleanText;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCampaignRequest {

    @Size(max = 100)
    @CleanText
    private String name;
    @Size(max = 80)
    private String slug;
    @Size(max = 500)
    @CleanText
    private String summary;
    @Size(max = 4000)
    @CleanText
    private String description;
    private Boolean progressionAgnostic;
    private CampaignCompletionMode completionMode;
    private Boolean playlistExportEnabled;
    private BigDecimal completionXp;
    @Size(max = 64)
    @CleanText
    private String creatorAlias;
    private Boolean seekingCuration;
    @Size(max = 512)
    private String backgroundUrl;
    @Pattern(regexp = "^$|^#?[A-Za-z0-9]{1,32}$|^(?:rgb|rgba|hsl|hsla)\\([0-9.,%\\s/]{1,64}\\)$", message = "must be a hex, named, or rgb/hsl color")
    private String backgroundColor;
    @Size(max = 512)
    private String iconUrl;
    @Size(max = 10)
    private List<UUID> tagIds;
}
