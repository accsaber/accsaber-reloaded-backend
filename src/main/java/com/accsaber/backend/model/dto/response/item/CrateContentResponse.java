package com.accsaber.backend.model.dto.response.item;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CrateContentResponse {

    private ItemResponse rewardItem;
    private Integer dropWeight;
    private BigDecimal dropChance;
}
