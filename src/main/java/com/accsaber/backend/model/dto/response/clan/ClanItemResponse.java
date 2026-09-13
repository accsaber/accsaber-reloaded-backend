package com.accsaber.backend.model.dto.response.clan;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.entity.clan.ClanItemSource;

public record ClanItemResponse(ItemResponse item, ClanItemSource source, Instant acquiredAt, boolean equipped) {
}
