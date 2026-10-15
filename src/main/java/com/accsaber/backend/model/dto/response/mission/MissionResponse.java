package com.accsaber.backend.model.dto.response.mission;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MissionResponse {

    private UUID id;
    private UUID parentMissionId;
    private Boolean perMember;
    private String code;
    private String name;
    private String description;
    private String type;
    private String pool;
    private String status;
    private String band;

    private UUID categoryId;
    private String categoryCode;

    private UUID targetMapDifficultyId;
    private PublicMapDifficultyResponse targetMap;
    private String targetPlayerId;
    private String targetPlayerName;

    private Double targetAcc;
    private Double targetAp;
    private Integer targetScore;
    private Integer targetCount;
    private Integer targetXp;
    private Double targetThresholdAp;
    private Integer targetStreak;
    private Instant targetRankedBefore;
    private Boolean targetCuratedOnly;

    private Integer progressCount;
    private Double progressAp;
    private Double progressValue;
    private Double targetValue;
    private Long contributors;
    private Double yourContribution;
    private Boolean endsWithWeek;
    private Integer xpReward;
    private Integer clanXpReward;
    private Integer standingReward;
    private ItemResponse itemReward;

    private Instant assignedAt;
    private Instant expiresAt;
    private Instant completedAt;

    private Instant unlocksAt;
    private Instant completableUntil;
    private Integer week;
    private Boolean unlocked;
    private Boolean open;
    private Boolean repeatable;
    private Integer maxCompletions;
}
