package com.accsaber.backend.model.dto.request.map;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SuggestMapRequest(
        @NotBlank String blLeaderboardId,
        String ssLeaderboardId,
        @NotNull UUID categoryId) {
}
