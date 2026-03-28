package com.accsaber.backend.model.dto.platform.scoresaber;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreSaberPlayerResponse {

    private String id;
    private String name;
    private String profilePicture;
    private String country;
    private boolean inactive;
}
