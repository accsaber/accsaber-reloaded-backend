package com.accsaber.backend.model.dto.request.map;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BulkReweightRequest {

    @NotEmpty
    @Valid
    private List<Item> items;

    private String reason;

    @Data
    public static class Item {

        @NotNull
        private UUID mapDifficultyId;

        @NotNull
        @DecimalMin("0.0")
        private BigDecimal complexity;
    }
}
