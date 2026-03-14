package com.accsaber.backend.model.dto.request.map;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImportMapFromLeaderboardIdsRequest {

    @NotBlank
    private String blLeaderboardId;

    @NotBlank
    private String ssLeaderboardId;

    @NotNull
    private UUID categoryId;

    @NotNull
    private Difficulty difficulty;

    @NotBlank
    private String characteristic;

    private UUID batchId;

    private Instant rankedAt;
}
