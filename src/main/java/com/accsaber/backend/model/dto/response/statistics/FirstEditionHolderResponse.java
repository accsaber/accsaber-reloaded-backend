package com.accsaber.backend.model.dto.response.statistics;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FirstEditionHolderResponse {

    private UUID itemId;
    private String itemName;
    private String iconUrl;
    private String rarity;
    private String typeKey;
    private UUID linkId;
    private Long serialNumber;
    private String userId;
    private String userName;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private String country;
}
