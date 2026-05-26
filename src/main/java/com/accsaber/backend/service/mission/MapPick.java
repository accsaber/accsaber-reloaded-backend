package com.accsaber.backend.service.mission;

import java.math.BigDecimal;

import com.accsaber.backend.model.entity.map.MapDifficulty;

record MapPick(MapDifficulty difficulty, BigDecimal complexity, Integer maxScore) {
}
