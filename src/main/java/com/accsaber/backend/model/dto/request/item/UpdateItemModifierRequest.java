package com.accsaber.backend.model.dto.request.item;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class UpdateItemModifierRequest {

    private BigDecimal globalDropChance;
    private String seasonStart;
    private String seasonEnd;
}
