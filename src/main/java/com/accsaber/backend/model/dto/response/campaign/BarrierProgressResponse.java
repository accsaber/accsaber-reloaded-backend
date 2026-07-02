package com.accsaber.backend.model.dto.response.campaign;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BarrierProgressResponse {

    private CampaignBarrierResponse barrier;
    private BigDecimal currentValue;
    private boolean satisfied;
    private boolean unlocked;
}
