package com.accsaber.backend.model.dto.response.item;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CrateModifierResponse {

    private UserItemResponse.ModifierRef modifier;
    private BigDecimal dropChance;
}
