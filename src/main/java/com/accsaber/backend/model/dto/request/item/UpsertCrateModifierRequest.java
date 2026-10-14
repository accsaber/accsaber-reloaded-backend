package com.accsaber.backend.model.dto.request.item;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UpsertCrateModifierRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") Double dropChance) {
}
