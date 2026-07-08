package com.accsaber.backend.model.dto.response.item;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DisintegrationResponse {

    private UUID linkId;
    private UUID itemId;
    private long quantityDisintegrated;
    private Long remainingQuantity;
    private BigDecimal essenceGained;
    private BigDecimal balance;
}
