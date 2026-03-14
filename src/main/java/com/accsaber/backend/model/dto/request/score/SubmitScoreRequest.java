package com.accsaber.backend.model.dto.request.score;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitScoreRequest {

    @NotNull
    private Long userId;

    @NotNull
    private UUID mapDifficultyId;

    @NotNull
    private Integer score;

    @NotNull
    private Integer scoreNoMods;

    @NotNull
    private Integer rank;

    @NotNull
    private Integer rankWhenSet;

    private Long blScoreId;
    private Integer maxCombo;
    private Integer badCuts;
    private Integer misses;
    private Integer wallHits;
    private Integer bombHits;
    private Integer pauses;
    private Integer streak115;
    private Integer playCount;
    private String hmd;
    private Instant timeSet;
    private List<UUID> modifierIds;
}
