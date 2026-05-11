package com.accsaber.backend.model.dto.response.item;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemModifierResponse {

    private UUID id;
    private String key;
    private String name;
    private String description;
    private String colorHex;
    private Object effectSpec;
    private boolean active;
    private Instant createdAt;
}
