package com.accsaber.backend.model.dto.platform.scoresaber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberScoreStats {

    private Integer max115Streak;
    private Integer leftBombs;
    private Integer rightBombs;
}
