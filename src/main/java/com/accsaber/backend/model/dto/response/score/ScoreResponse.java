package com.accsaber.backend.model.dto.response.score;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.map.Difficulty;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScoreResponse {

    private UUID id;
    private String userId;
    private String userName;
    private String avatarUrl;
    private String country;
    private UUID mapDifficultyId;
    private UUID mapId;
    private String songHash;
    private String songName;
    private String songAuthor;
    private String mapAuthor;
    private String coverUrl;
    private Difficulty difficulty;
    private UUID categoryId;
    private Integer score;
    private Integer scoreNoMods;
    private BigDecimal accuracy;
    private Integer rank;
    private Integer rankWhenSet;
    private BigDecimal ap;
    private BigDecimal weightedAp;
    private Long blScoreId;
    private Integer maxCombo;
    private Integer badCuts;
    private Integer misses;
    private Integer wallHits;
    private Integer bombHits;
    private Integer pauses;
    private Integer streak115;
    private Integer playCount;
    private String hmd;
    private Instant timeSet;
    private boolean reweightDerivative;
    private BigDecimal xpGained;
    private BigDecimal baseXp;
    private BigDecimal bonusXp;
    private boolean active;
    private List<UUID> modifierIds;
    private Instant createdAt;
}
