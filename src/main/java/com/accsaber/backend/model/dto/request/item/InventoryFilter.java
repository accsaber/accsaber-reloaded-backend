package com.accsaber.backend.model.dto.request.item;

import java.util.List;

import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.item.ItemSource;

public record InventoryFilter(
        List<String> typeKey,
        List<ItemRarity> rarity,
        List<String> modifierKey,
        Boolean tradeable,
        String search,
        List<ItemSource> source,
        Boolean deprecated) {

    public List<String> typeKeysOrNull() {
        return nullIfEmpty(typeKey);
    }

    public List<ItemRarity> raritiesOrNull() {
        return nullIfEmpty(rarity);
    }

    public List<String> modifierKeysOrNull() {
        return nullIfEmpty(modifierKey);
    }

    public List<ItemSource> sourcesOrNull() {
        return nullIfEmpty(source);
    }

    public String searchOrNull() {
        return search == null || search.isBlank() ? null : search.trim();
    }

    public Boolean deprecatedEffective() {
        return deprecated == null ? Boolean.FALSE : deprecated;
    }

    private static <T> List<T> nullIfEmpty(List<T> values) {
        return values == null || values.isEmpty() ? null : values;
    }
}
