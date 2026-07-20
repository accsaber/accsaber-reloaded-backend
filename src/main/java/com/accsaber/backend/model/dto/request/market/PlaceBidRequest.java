package com.accsaber.backend.model.dto.request.market;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlaceBidRequest {

    @NotNull
    @Min(1)
    private Long amount;
}
