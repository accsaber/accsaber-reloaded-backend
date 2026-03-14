package com.accsaber.backend.model.dto.response.milestone;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LevelResponse {

    private int level;
    private String title;
    private BigDecimal totalXp;
    private BigDecimal xpForCurrentLevel;
    private BigDecimal xpForNextLevel;
    private BigDecimal progressPercent;
}
