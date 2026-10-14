package com.accsaber.backend.model.dto.request.item;

public record UpdateUnusualEffectRequest(
        String name,
        String description,
        Object effectSpec) {
}
