package com.accsaber.backend.model.dto.request.campaign;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetCampaignItemRequest {

    @NotNull
    private UUID itemId;

    @Min(1)
    private Integer quantity;
}
