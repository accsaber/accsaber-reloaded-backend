package com.accsaber.backend.model.dto.request.clan;

import java.util.UUID;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ClanWarRewardItemRequest {

    private UUID itemId;

    @Positive
    private Integer quantity;

    @Positive
    private Integer topContributors;

    private Boolean active;
}
