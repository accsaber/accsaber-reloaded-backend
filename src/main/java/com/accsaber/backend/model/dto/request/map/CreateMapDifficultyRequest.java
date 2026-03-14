package com.accsaber.backend.model.dto.request.map;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateMapDifficultyRequest {

    @NotBlank
    private String songName;

    @NotBlank
    private String songAuthor;

    @NotBlank
    private String songHash;

    @NotBlank
    private String mapAuthor;

    private String beatsaverCode;

    private String coverUrl;

    @NotNull
    private UUID categoryId;

    @NotNull
    private Difficulty difficulty;

    @NotBlank
    private String characteristic;

    private String ssLeaderboardId;

    private String blLeaderboardId;

    @NotNull
    @Positive
    private Integer maxScore;

    private UUID batchId;

    private Instant rankedAt;
}
