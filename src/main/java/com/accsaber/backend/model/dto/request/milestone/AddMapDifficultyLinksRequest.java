package com.accsaber.backend.model.dto.request.milestone;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

public record AddMapDifficultyLinksRequest(
        @NotEmpty List<UUID> mapDifficultyIds) {
}
