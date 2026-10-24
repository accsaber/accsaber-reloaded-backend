package com.accsaber.backend.model.dto.response.common;

import java.util.UUID;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;

public record MapDifficultyRef(UUID id, UUID mapId, String songName, String songSubName, String songAuthor,
        String mapAuthor, String coverUrl, String cdnCoverUrl, Difficulty difficulty, UUID categoryId,
        String categoryName, String beatsaverCode, String characteristic) {

    public static MapDifficultyRef of(MapDifficulty difficulty) {
        if (difficulty == null) {
            return null;
        }
        Map map = difficulty.getMap();
        Category category = difficulty.getCategory();
        return new MapDifficultyRef(difficulty.getId(), map.getId(), map.getSongName(), map.getSongSubName(),
                map.getSongAuthor(), map.getMapAuthor(), map.getCoverUrl(), map.getCdnCoverUrl(),
                difficulty.getDifficulty(), category != null ? category.getId() : null,
                category != null ? category.getName() : null, map.getBeatsaverCode(), difficulty.getCharacteristic());
    }
}
