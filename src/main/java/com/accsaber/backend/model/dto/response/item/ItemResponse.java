package com.accsaber.backend.model.dto.response.item;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemResponse {

    private UUID id;
    private UUID typeId;
    private String typeKey;
    private String name;
    private String description;
    private String iconUrl;
    private Object value;
    private String rarity;
    private boolean tradeable;
    private boolean visible;
    private boolean active;
    private boolean deprecated;
    private boolean stackable;
    private boolean welcomeGrant;
    private boolean missionPoolable;
    private BigDecimal worth;
    private String requirement;
    private Integer unlockLevel;
    private Instant createdAt;
}
