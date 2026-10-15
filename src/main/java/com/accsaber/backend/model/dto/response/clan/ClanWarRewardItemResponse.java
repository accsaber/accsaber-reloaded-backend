package com.accsaber.backend.model.dto.response.clan;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.ItemResponse;

public record ClanWarRewardItemResponse(UUID id, ItemResponse item, int quantity, Integer topContributors,
        boolean active) {
}
