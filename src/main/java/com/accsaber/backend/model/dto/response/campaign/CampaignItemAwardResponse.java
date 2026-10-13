package com.accsaber.backend.model.dto.response.campaign;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.ItemResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignItemAwardResponse {

    private UUID itemId;
    private String itemName;
    private Integer quantity;
    private ItemResponse item;
}
