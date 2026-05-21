package com.accsaber.backend.model.dto.request.item;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertCrateContentRequest {

    @NotNull
    @Min(1)
    private Integer dropWeight;
}
