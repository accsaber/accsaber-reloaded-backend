package com.accsaber.backend.model.dto.platform.scoresaber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberLeaderboardResponse {

    private Long id;
    private Map map;
    private Difficulty difficulty;
    private Integer maxScore;
    private Integer totalScores;
    private Integer dailyScores;
    private String createdAt;
    private Realm realm;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Map {
        private Long id;
        private String hash;
        private String bsid;
        private String songName;
        private String songSubName;
        private String songAuthorName;
        private String levelAuthorName;
        private Double bpm;
        private String coverUrl;
        private Boolean verified;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Difficulty {
        private Long id;
        private Integer difficulty;
        private String rawDifficulty;
        private String gameMode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Realm {
        private Long realmId;
        private String realmName;
        private String leaderboardStatus;
        private Boolean positiveModifiers;
        private Double stars;
        private String rankedAt;
        private String qualifiedAt;
        private String lovedAt;
    }
}
