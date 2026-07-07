package com.accsaber.backend.model.dto.request.item;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EquipItemRequest {

    @NotNull
    private UUID linkId;

    @Size(max = 60)
    private String variantKey;
}
