package com.accsaber.backend.model.dto.request.map;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveReweightRequest {

    @NotNull
    private UUID mapDifficultyId;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal complexity;

    private String reason;
}
