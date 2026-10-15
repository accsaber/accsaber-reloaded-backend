package com.accsaber.backend.model.dto.response.clan;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.ItemResponse;

public record ClanSeasonRewardResponse(UUID id, int rankFrom, int rankTo, ItemResponse item, int quantity) {
}
