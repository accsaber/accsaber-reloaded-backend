package com.accsaber.backend.model.dto.request.item;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpsertCrateContentRequest(
        @NotNull @Min(1) Integer dropWeight) {
}
