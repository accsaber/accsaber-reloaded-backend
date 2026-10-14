package com.accsaber.backend.model.dto.request.item;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EquipItemRequest(
        @NotNull UUID linkId,
        @Size(max = 60) String variantKey) {
}
