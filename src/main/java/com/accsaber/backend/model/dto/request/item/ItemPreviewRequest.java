package com.accsaber.backend.model.dto.request.item;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ItemPreviewRequest {

    @NotNull
    private UUID itemId;

    private UUID unusualEffectId;

    private List<String> modifierKeys;

    @Size(max = 60)
    private String variantKey;
}
