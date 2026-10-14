package com.accsaber.backend.model.dto.request.item;

public record UpdateItemModifierRequest(
        Double globalDropChance,
        String seasonStart,
        String seasonEnd) {
}
