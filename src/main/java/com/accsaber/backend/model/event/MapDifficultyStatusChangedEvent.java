package com.accsaber.backend.model.event;

import com.accsaber.backend.model.entity.map.MapDifficulty;

public record MapDifficultyStatusChangedEvent(MapDifficulty difficulty) {
}
