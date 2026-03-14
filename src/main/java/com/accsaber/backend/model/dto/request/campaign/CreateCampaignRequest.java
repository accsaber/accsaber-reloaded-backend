package com.accsaber.backend.model.dto.request.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCampaignRequest {

    @NotNull
    private Long creatorId;

    @NotBlank
    private String name;

    private String description;

    private String difficulty;
}
