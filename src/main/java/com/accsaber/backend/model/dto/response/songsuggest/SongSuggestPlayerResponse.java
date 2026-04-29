package com.accsaber.backend.model.dto.response.songsuggest;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonPropertyOrder({ "top10kScore", "id", "name", "rank" })
public class SongSuggestPlayerResponse {

    String id;
    String name;
    int rank;

    @JsonProperty("top10kScore")
    List<SongSuggestScoreResponse> top10kScore;
}
