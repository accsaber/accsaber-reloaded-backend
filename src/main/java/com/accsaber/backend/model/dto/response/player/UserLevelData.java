package com.accsaber.backend.model.dto.response.player;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserLevelData {

    int level;
    String title;
    BigDecimal xpForCurrentLevel;
    BigDecimal xpForNextLevel;
    BigDecimal progressPercent;
}
