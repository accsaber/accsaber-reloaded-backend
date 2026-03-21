package com.accsaber.backend.model.dto.request.milestone;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.entity.milestone.MilestoneTier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMilestoneRequest {

    @NotNull
    private UUID setId;

    private UUID categoryId;

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String type;

    @NotNull
    private MilestoneTier tier;

    @NotNull
    private BigDecimal xp;

    @NotNull
    @Valid
    private MilestoneQuerySpec querySpec;

    @NotNull
    private BigDecimal targetValue;

    @NotNull
    private String comparison = "GTE";

    private boolean blExclusive = false;

    private List<UUID> mapDifficultyIds;
}
