package com.accsaber.backend.model.dto.platform.ai;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiComplexityResponse {

    private BigDecimal balanced;

    @JsonProperty("passing_difficulty")
    private BigDecimal passingDifficulty;

    @JsonProperty("expected_acc")
    private BigDecimal expectedAcc;
}
