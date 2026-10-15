package com.accsaber.backend.model.dto.projection;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;
import com.accsaber.backend.model.entity.map.Difficulty;

public record ReweightRoundMapRow(UUID roundId, MapDifficultyRef map, Double from, Double to) {

    public ReweightRoundMapRow(UUID roundId, UUID mapDifficultyId, UUID mapId, String songName, String songSubName,
            String songAuthor, String mapAuthor, String coverUrl, String cdnCoverUrl, Difficulty difficulty,
            UUID categoryId, String categoryName, Double from, Double to) {
        this(roundId, new MapDifficultyRef(mapDifficultyId, mapId, songName, songSubName, songAuthor, mapAuthor,
                coverUrl, cdnCoverUrl, difficulty, categoryId, categoryName), from, to);
    }
}
