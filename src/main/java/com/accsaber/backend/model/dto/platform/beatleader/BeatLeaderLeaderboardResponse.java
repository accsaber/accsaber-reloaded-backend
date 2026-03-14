package com.accsaber.backend.model.dto.platform.beatleader;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeatLeaderLeaderboardResponse {

    private String id;
    private Long plays;
    private Song song;
    private DifficultyDescription difficulty;
    private List<BeatLeaderScoreResponse> scores;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Song {
        private String hash;
        private String name;
        private String author;
        private String mapper;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DifficultyDescription {
        private String difficultyName;
        private String modeName;
        private Integer maxScore;
    }
}
