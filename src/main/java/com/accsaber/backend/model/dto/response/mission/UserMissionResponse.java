package com.accsaber.backend.model.dto.response.mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.util.MissionDescriptionRenderer;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserMissionResponse {

    private UUID id;
    private String name;
    private String description;
    private String type;
    private String pool;
    private String status;
    private String band;

    private UUID categoryId;
    private String categoryCode;

    private UUID targetMapDifficultyId;
    private String targetMapSongName;
    private String targetPlayerId;
    private String targetPlayerName;

    private BigDecimal targetAcc;
    private BigDecimal targetAp;
    private Integer targetScore;
    private Integer targetCount;
    private Integer targetXp;
    private BigDecimal targetThresholdAp;
    private Integer targetStreak;

    private Integer progressCount;
    private Integer xpReward;
    private UUID itemRewardId;
    private String itemRewardName;

    private Instant assignedAt;
    private Instant expiresAt;
    private Instant completedAt;

    public static UserMissionResponse from(UserMission m) {
        return UserMissionResponse.builder()
                .id(m.getId())
                .name(m.getTemplate().getName())
                .description(renderDescription(m))
                .type(m.getTemplate().getType().name())
                .pool(m.getPool().name())
                .status(m.getStatus().name())
                .band(m.getBand() != null ? m.getBand().name() : null)
                .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                .categoryCode(m.getCategory() != null ? m.getCategory().getCode() : null)
                .targetMapDifficultyId(m.getTargetMapDifficulty() != null
                        ? m.getTargetMapDifficulty().getId() : null)
                .targetMapSongName(m.getTargetMapDifficulty() != null
                        && m.getTargetMapDifficulty().getMap() != null
                                ? m.getTargetMapDifficulty().getMap().getSongName() : null)
                .targetPlayerId(m.getTargetPlayer() != null
                        ? String.valueOf(m.getTargetPlayer().getId()) : null)
                .targetPlayerName(m.getTargetPlayer() != null
                        ? m.getTargetPlayer().getName() : null)
                .targetAcc(roundAcc(m.getTargetAcc()))
                .targetAp(roundAp(m.getTargetAp()))
                .targetScore(m.getTargetScore())
                .targetCount(m.getTargetCount())
                .targetXp(m.getTargetXp())
                .targetThresholdAp(roundAp(m.getTargetThresholdAp()))
                .targetStreak(m.getTargetStreak())
                .progressCount(m.getProgressCount())
                .xpReward(m.getXpReward())
                .itemRewardId(m.getItemReward() != null ? m.getItemReward().getId() : null)
                .itemRewardName(m.getItemReward() != null ? m.getItemReward().getName() : null)
                .assignedAt(m.getAssignedAt())
                .expiresAt(m.getExpiresAt())
                .completedAt(m.getCompletedAt())
                .build();
    }

    private static BigDecimal roundAcc(BigDecimal acc) {
        return acc == null ? null : acc.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal roundAp(BigDecimal ap) {
        return ap == null ? null : ap.setScale(0, RoundingMode.HALF_UP);
    }

    public static String renderDescription(UserMission m) {
        return MissionDescriptionRenderer.render(m.getTemplate().getDescription(),
                new MissionDescriptionRenderer.Values(
                        m.getTargetCount(), m.getTargetXp(), m.getTargetAcc(), m.getTargetAp(),
                        m.getTargetScore(), m.getTargetThresholdAp(), m.getTargetStreak(),
                        MissionDescriptionRenderer.formatMap(m.getTargetMapDifficulty()),
                        m.getTargetPlayer() != null ? m.getTargetPlayer().getName() : null,
                        m.getCategory() != null ? m.getCategory().getName() : null));
    }
}
