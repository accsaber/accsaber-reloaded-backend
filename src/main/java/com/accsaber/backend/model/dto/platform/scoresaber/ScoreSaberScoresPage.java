package com.accsaber.backend.model.dto.platform.scoresaber;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberScoresPage {

    private List<ScoreSaberScoreResponse> scores;
    private Metadata metadata;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        private Integer total;
        private Integer page;
        private Integer itemsPerPage;
    }
}
