package com.accsaber.backend.model.dto.platform.scoresaber;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberScoreResponse {

    private Long id;
    private Integer rank;
    private Integer unmodifiedScore;
    private List<String> mods;
    private Integer badCuts;
    private Integer missedNotes;
    private Integer maxCombo;
    private Boolean hasReplay;
    private Integer legacyHmdId;
    private String createdAt;
    private Player player;
    private Device device;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Player {
        private String id;
        private String name;
        private String country;
        private String avatar;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Device {
        private String hmd;
    }
}
