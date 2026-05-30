package com.accsaber.backend.model.dto.response.mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.entity.mission.UserMission;
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
        String tpl = m.getTemplate().getDescription();
        if (tpl == null || tpl.isBlank())
            return tpl;
        String out = tpl;
        if (out.contains("{count}"))
            out = out.replace("{count}", m.getTargetCount() != null ? m.getTargetCount().toString() : "?");
        if (out.contains("{xp}"))
            out = out.replace("{xp}", m.getTargetXp() != null ? m.getTargetXp().toString() : "?");
        if (out.contains("{acc}"))
            out = out.replace("{acc}", formatAcc(m.getTargetAcc()));
        if (out.contains("{ap}"))
            out = out.replace("{ap}", formatAp(m.getTargetAp()));
        if (out.contains("{score}"))
            out = out.replace("{score}", m.getTargetScore() != null ? m.getTargetScore().toString() : "?");
        if (out.contains("{threshold}"))
            out = out.replace("{threshold}", formatAp(m.getTargetThresholdAp()));
        if (out.contains("{streak}"))
            out = out.replace("{streak}", m.getTargetStreak() != null ? m.getTargetStreak().toString() : "?");
        if (out.contains("{map}"))
            out = out.replace("{map}", formatMap(m));
        if (out.contains("{player}"))
            out = out.replace("{player}", m.getTargetPlayer() != null ? m.getTargetPlayer().getName() : "another player");
        if (out.contains("{category}"))
            out = out.replace("{category}", m.getCategory() != null ? m.getCategory().getName() : "any category");
        return out;
    }

    private static String formatAcc(BigDecimal acc) {
        if (acc == null)
            return "?";
        return acc.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private static String formatAp(BigDecimal ap) {
        if (ap == null)
            return "?";
        return ap.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatMap(UserMission m) {
        if (m.getTargetMapDifficulty() == null || m.getTargetMapDifficulty().getMap() == null)
            return "a ranked map";
        String song = m.getTargetMapDifficulty().getMap().getSongName();
        String diff = m.getTargetMapDifficulty().getDifficulty() != null
                ? m.getTargetMapDifficulty().getDifficulty().name()
                : null;
        return diff != null ? song + " (" + diff + ")" : song;
    }
}
