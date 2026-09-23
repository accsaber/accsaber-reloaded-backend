package com.accsaber.backend.model.dto.projection;

import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;

public record ReweightRoundMapRow(UUID roundId, UUID mapId, UUID mapDifficultyId, String songName,
        Difficulty difficulty, Double from, Double to) {
}
