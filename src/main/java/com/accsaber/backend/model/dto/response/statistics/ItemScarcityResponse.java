package com.accsaber.backend.model.dto.response.statistics;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemScarcityResponse {

    private UUID itemId;
    private String itemName;
    private String iconUrl;
    private String rarity;
    private String typeKey;
    private long ownerCount;
}
