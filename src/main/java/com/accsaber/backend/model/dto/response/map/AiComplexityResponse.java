package com.accsaber.backend.model.dto.response.map;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AiComplexityResponse {
    BigDecimal complexity;
}
