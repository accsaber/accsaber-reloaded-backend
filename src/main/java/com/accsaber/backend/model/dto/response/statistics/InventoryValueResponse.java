package com.accsaber.backend.model.dto.response.statistics;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryValueResponse {

    private PlayerRef player;
    private Long itemsValue;
    private Long essenceBalance;
    private Long totalValue;
}
