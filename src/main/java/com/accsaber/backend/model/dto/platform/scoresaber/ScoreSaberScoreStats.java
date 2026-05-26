package com.accsaber.backend.model.dto.platform.scoresaber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberScoreStats {

    private Long totalScore;
    private Long maxScore;
    private Boolean passed;
    private Long failTime;
    private Long endTime;
    private Integer maxCombo;
    private Integer max115Streak;
    private Double fcAcc;
    private Double accLeft;
    private Double accRight;
    private Integer leftMiss;
    private Integer rightMiss;
    private Integer leftBadCuts;
    private Integer rightBadCuts;
    private Integer leftBombs;
    private Integer rightBombs;
}
