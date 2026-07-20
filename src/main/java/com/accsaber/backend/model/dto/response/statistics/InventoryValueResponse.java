package com.accsaber.backend.model.dto.response.statistics;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryValueResponse {

    private String userId;
    private String userName;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private String country;
    private Long itemsValue;
    private Long essenceBalance;
    private Long totalValue;
}
