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
    private Integer modifiedScore;
    private Double accuracy;
    private Double pp;
    private Double weight;
    private List<String> mods;
    private Integer badCuts;
    private Integer missedNotes;
    private Integer maxCombo;
    private Boolean fullCombo;
    private Boolean hasReplay;
    private Boolean personalBest;
    private Integer legacyHmdId;
    private String version;
    private String createdAt;
    private Player player;
    private Device device;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Player {
        private String id;
        private String name;
        private String country;
        private String role;
        private String avatar;
        private Integer permissions;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Device {
        private String hmd;
        private String controllerLeft;
        private String controllerRight;
    }
}
