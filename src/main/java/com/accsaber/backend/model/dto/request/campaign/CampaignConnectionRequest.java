package com.accsaber.backend.model.dto.request.campaign;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CampaignConnectionRequest {

    @NotNull
    private UUID comesFromCampaignDifficultyId;

    @Pattern(regexp = "^$|^#?[A-Za-z0-9]{1,32}$", message = "must be a hex or named color")
    private String color;
}
