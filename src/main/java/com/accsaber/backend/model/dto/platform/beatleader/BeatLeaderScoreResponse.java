package com.accsaber.backend.model.dto.platform.beatleader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeatLeaderScoreResponse {

    private Long id;
    private Integer baseScore;
    private Integer modifiedScore;
    private String modifiers;
    private Integer rank;
    private Integer wallsHit;
    private Integer bombCuts;
    private Integer pauses;
    private Integer maxStreak;
    private Integer playCount;
    private Integer maxCombo;
    private Integer badCuts;
    private Integer missedNotes;
    private Integer hmd;
    private String leaderboardId;
    private Long timepost;
    private Player player;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Player {
        private String id;
    }
}
