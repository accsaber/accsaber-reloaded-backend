package com.accsaber.backend.model.dto.response.score;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyMetadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class ScoreResponse {

    private UUID id;
    private String userId;
    private String userName;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private PublicClanResponse clan;
    private String country;
    private UUID mapDifficultyId;
    private UUID mapId;
    private String beatsaverCode;
    private String songHash;
    private String songName;
    private String songSubName;
    private String songAuthor;
    private String mapAuthor;
    private String coverUrl;
    private String cdnCoverUrl;
    private Difficulty difficulty;
    private String characteristic;
    private UUID categoryId;
    private Double complexity;
    private Integer score;
    private Integer scoreNoMods;
    private Double accuracy;
    private Integer rank;
    private Integer rankWhenSet;
    private Double ap;
    private Double weightedAp;
    private Long blScoreId;
    private Long ssScoreId;
    private Integer maxCombo;
    private Integer badCuts;
    private Integer misses;
    private Integer wallHits;
    private Integer bombHits;
    private Integer pauses;
    private Integer streak115;
    private Integer maxStreak115;
    private Integer playCount;
    private Instant lastPlayedAt;
    private String hmd;
    private Instant timeSet;
    private boolean reweightDerivative;
    private Double xpGained;
    private Integer baseXp;
    private Double bonusXp;
    private boolean active;
    private boolean partial;
    private String supersedesReason;
    private List<UUID> modifierIds;
    private Instant createdAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private MyScoreSummary myScore;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String supporterTier;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double skillLevel;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private MapDifficultyMetadata metadata;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double nps;
}
