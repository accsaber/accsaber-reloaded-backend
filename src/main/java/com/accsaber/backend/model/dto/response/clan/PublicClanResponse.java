package com.accsaber.backend.model.dto.response.clan;

import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.entity.clan.Clan;

public record PublicClanResponse(UUID id, String slug, String name, String tag, List<ItemResponse> equipped) {

    public static PublicClanResponse of(Clan clan, List<ItemResponse> equipped) {
        return new PublicClanResponse(clan.getId(), clan.getSlug(), clan.getName(), clan.getTag(), equipped);
    }
}
