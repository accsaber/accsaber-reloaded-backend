package com.accsaber.backend.model.dto.platform.beatsaver;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeatSaverMapResponse {

    private String id;
    private Metadata metadata;
    private List<Version> versions;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        private String songName;
        private String songSubName;
        private String songAuthorName;
        private String levelAuthorName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Version {
        private String hash;
        private String coverURL;
        private String downloadURL;
        private List<Diff> diffs;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Diff {
        private String difficulty;
        private String characteristic;
        private Integer maxScore;
    }
}
