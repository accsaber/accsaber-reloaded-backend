package com.accsaber.backend.model.dto.response.statistics;

import java.math.BigDecimal;

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
    private BigDecimal itemsValue;
    private BigDecimal essenceBalance;
    private BigDecimal totalValue;
}
