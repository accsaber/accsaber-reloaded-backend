package com.accsaber.backend.model.dto.response.campaign;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignItemAwardResponse {

    private UUID itemId;
    private String itemName;
    private Integer quantity;
}
