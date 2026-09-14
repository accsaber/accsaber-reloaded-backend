package com.accsaber.backend.model.dto.response.statistics;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryValueResponse {

    private String userId;
    private String userName;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private PublicClanResponse clan;
    private String country;
    private Long itemsValue;
    private Long essenceBalance;
    private Long totalValue;
}
