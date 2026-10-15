package com.accsaber.backend.model.dto.platform.scoresaber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberLeaderboardResponse {

    private Long id;
    private Map map;
    private Integer maxScore;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Map {
        private String hash;
        private String songName;
        private String songAuthorName;
        private String levelAuthorName;
    }
}
