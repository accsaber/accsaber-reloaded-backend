package com.accsaber.backend.model.dto.request.item;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AwardItemRequest {

    @NotNull
    private Long userId;

    @NotNull
    private UUID itemId;

    private String reason;

    private List<String> modifierKeys;

    private UUID unusualEffectId;

    @Min(1)
    private Long quantity;
}
