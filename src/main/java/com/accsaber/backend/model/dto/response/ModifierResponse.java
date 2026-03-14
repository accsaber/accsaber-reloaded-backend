package com.accsaber.backend.model.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ModifierResponse {

    UUID id;
    String name;
    String code;
    BigDecimal multiplier;
}
