package com.accsaber.backend.model.dto.platform.scoresaber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberPlayerResponse {

    private String id;
    private String name;
    private String country;
    private String role;
    private String avatar;
    private Integer permissions;
    private boolean banned;
    private boolean silenced;
    private boolean inactive;
    private Stats stats;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Stats {
        private Long realmId;
        private String realmName;
        private Long rank;
        private Long countryRank;
        private Double totalPP;
        private String totalScore;
        private String totalRankedScore;
        private Long totalPlayedLeaderboards;
        private Long totalPlayedRankedLeaderboards;
        private Long totalSubmittedPlays;
        private Long totalReplayViews;
        private Double averageAccuracy;
        private Double weightedAverageAccuracy;
        private Double completionAccuracy;
        private Device device;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Device {
        private String hmd;
        private String controllerLeft;
        private String controllerRight;
    }
}
