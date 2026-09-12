package com.accsaber.backend.model.dto.request.map;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshMapDifficultyRequest {

    @NotBlank
    private String blLeaderboardId;

    private String ssLeaderboardId;
}
