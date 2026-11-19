package com.accsaber.backend.model.dto.request.map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeactivateMapDifficultyRequest(@NotBlank @Size(max = 2000) String reason) {
}
