package com.accsaber.backend.model.dto.response.map;

import java.math.BigDecimal;

import com.accsaber.backend.model.entity.map.Difficulty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RankedDifficultyResponse {
    String songHash;
    Difficulty difficulty;
    BigDecimal complexity;
}
