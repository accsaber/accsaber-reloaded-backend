package com.accsaber.backend.model.dto.request.campaign;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddCampaignMapRequest {

    @NotNull
    private UUID mapDifficultyId;

    @NotNull
    private BigDecimal accuracyRequirement;

    private BigDecimal xp;

    private List<UUID> prerequisiteCampaignMapIds;
}
