package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserMapImprovementsResponse {

    @JsonUnwrapped
    private UserIdentity user;
    private UUID mapDifficultyId;
    private UUID mapId;
    private String songName;
    private String songSubName;
    private String songAuthor;
    private String mapAuthor;
    private String coverUrl;
    private String cdnCoverUrl;
    private Difficulty difficulty;
    private UUID categoryId;
    private String categoryName;
    private long improvementCount;
    private UUID latestScoreId;
    private Instant latestScoreTimeSet;
}
