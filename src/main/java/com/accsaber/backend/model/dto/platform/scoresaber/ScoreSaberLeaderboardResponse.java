package com.accsaber.backend.model.dto.platform.scoresaber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberLeaderboardResponse {

    private Long id;
    private String songHash;
    private String songName;
    private String songAuthorName;
    private String levelAuthorName;
    private Integer maxScore;
}
