package com.accsaber.backend.model.dto.request.market;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceBidRequest(
        @NotNull @Min(1) Long amount) {
}
