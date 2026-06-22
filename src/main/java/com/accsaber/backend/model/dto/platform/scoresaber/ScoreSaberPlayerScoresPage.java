package com.accsaber.backend.model.dto.platform.scoresaber;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberPlayerScoresPage {

    private List<Entry> data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private ScoreSaberScoreResponse score;
    }
}
