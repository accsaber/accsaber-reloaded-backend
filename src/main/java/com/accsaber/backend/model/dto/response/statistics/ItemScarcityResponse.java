package com.accsaber.backend.model.dto.response.statistics;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.ItemResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class ItemScarcityResponse {

    private UUID itemId;
    private ItemResponse item;
    private long ownerCount;
    private long instanceCount;
}
