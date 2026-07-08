package com.accsaber.backend.model.dto.request.item;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertCrateModifierRequest {

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @DecimalMax("1")
    private BigDecimal dropChance;
}
