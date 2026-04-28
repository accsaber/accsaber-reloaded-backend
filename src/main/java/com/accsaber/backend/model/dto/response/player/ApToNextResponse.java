package com.accsaber.backend.model.dto.response.player;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ApToNextResponse {

    String userId;
    String categoryCode;
    BigDecimal rawApForOneGain;
}
