package com.accsaber.backend.model.dto.response.statistics;

import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;

public record MapIdentity(UUID mapDifficultyId, UUID mapId, String songName, String songSubName, String songAuthor,
        String mapAuthor, String coverUrl, String cdnCoverUrl, Difficulty difficulty, UUID categoryId,
        String categoryName) {
}
