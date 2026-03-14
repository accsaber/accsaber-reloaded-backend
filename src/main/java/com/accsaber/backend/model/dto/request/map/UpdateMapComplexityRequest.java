package com.accsaber.backend.model.dto.request.map;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class UpdateMapComplexityRequest {

    @NotNull
    @Positive
    private BigDecimal complexity;

    @NotNull
    private String reason;
}
