package com.accsaber.backend.model.dto.platform.scoresaber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberScoreResponse {

    private Long id;
    private Integer baseScore;
    private Integer modifiedScore;
    private String modifiers;
    private Integer rank;
    private Integer maxCombo;
    private Integer badCuts;
    private Integer missedNotes;
    private String deviceHmd;
    private String timeSet;
    private LeaderboardPlayerInfo leaderboardPlayerInfo;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeaderboardPlayerInfo {
        private String id;
    }
}
