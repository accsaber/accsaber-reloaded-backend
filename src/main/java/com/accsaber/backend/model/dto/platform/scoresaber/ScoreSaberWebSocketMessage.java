package com.accsaber.backend.model.dto.platform.scoresaber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberWebSocketMessage {

    private String commandName;
    private CommandData commandData;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommandData {
        private ScoreSaberScoreResponse score;
        private ScoreSaberLeaderboardResponse leaderboard;
    }
}
