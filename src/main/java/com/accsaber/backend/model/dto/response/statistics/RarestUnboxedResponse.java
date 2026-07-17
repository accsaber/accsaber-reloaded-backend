package com.accsaber.backend.model.dto.response.statistics;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RarestUnboxedResponse {

    private UUID linkId;
    private String userId;
    private String userName;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private String country;
    private UUID itemId;
    private String itemName;
    private String iconUrl;
    private String rarity;
    private String typeKey;
    private Long serialNumber;
    private long modifierCount;
    private List<String> modifiers;
    private String unusualEffect;
}
