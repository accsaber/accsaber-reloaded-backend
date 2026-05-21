package com.accsaber.backend.model.dto.request.score;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class PluginSubmitRequest {

    @NotBlank
    private String nonce;

    @NotNull
    private UUID mapDifficultyId;

    @NotNull
    @PositiveOrZero
    private Integer score;

    @NotNull
    @PositiveOrZero
    private Integer scoreNoMods;

    private Integer maxCombo;
    private Integer badCuts;
    private Integer misses;
    private Integer wallHits;
    private Integer bombHits;
    private Integer pauses;
    private Integer streak115;
    private String hmd;
    private Instant timeSet;
    private List<String> modifierCodes;
    private boolean partial;
}
