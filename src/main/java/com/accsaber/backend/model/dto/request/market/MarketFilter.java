package com.accsaber.backend.model.dto.request.market;

import java.util.List;

import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.market.MarketListingStatus;

public record MarketFilter(
        MarketListingStatus status,
        Long sellerId,
        List<String> typeKey,
        List<ItemRarity> rarity,
        List<String> modifierKey,
        List<String> effectKey,
        MarketKind kind,
        Long minPrice,
        Long maxPrice,
        String search,
        MarketSortOption sort) {

    public static MarketFilter empty() {
        return new MarketFilter(null, null, null, null, null, null, null, null, null, null, null);
    }

    public MarketListingStatus statusOrActive() {
        return status == null ? MarketListingStatus.active : status;
    }

    public List<String> typeKeysOrNull() {
        return nullIfEmpty(typeKey);
    }

    public List<ItemRarity> raritiesOrNull() {
        return nullIfEmpty(rarity);
    }

    public List<String> modifierKeysOrNull() {
        return nullIfEmpty(modifierKey);
    }

    public List<String> effectKeysOrNull() {
        return nullIfEmpty(effectKey);
    }

    public boolean auctionsOnly() {
        return kind == MarketKind.auction;
    }

    public boolean buyoutOnly() {
        return kind == MarketKind.shop;
    }

    public String searchOrNull() {
        return search == null || search.isBlank() ? null : search.trim();
    }

    private static <T> List<T> nullIfEmpty(List<T> values) {
        return values == null || values.isEmpty() ? null : values;
    }
}
