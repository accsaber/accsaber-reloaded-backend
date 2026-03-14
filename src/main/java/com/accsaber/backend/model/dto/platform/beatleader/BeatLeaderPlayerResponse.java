package com.accsaber.backend.model.dto.platform.beatleader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeatLeaderPlayerResponse {

    private String id;
    private String name;
    private String avatar;
    private String country;
}
