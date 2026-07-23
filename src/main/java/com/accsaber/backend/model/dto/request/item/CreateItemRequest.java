package com.accsaber.backend.model.dto.request.item;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.entity.item.ItemRarity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateItemRequest {

    @NotNull
    private UUID typeId;

    @NotBlank
    private String name;

    private String description;
    private String iconUrl;
    private Map<String, Object> value;

    private ItemRarity rarity = ItemRarity.common;
    private boolean tradeable = false;
    private boolean visible = true;
    private boolean stackable = false;
    private boolean welcomeGrant = false;
    private boolean missionPoolable = false;
    private boolean downloadable = false;
    private boolean uniquePerUser = false;
    private boolean serialized = true;
    private boolean active = true;
    private Long worth;
    private String requirement;
    private Integer unlockLevel;
}
