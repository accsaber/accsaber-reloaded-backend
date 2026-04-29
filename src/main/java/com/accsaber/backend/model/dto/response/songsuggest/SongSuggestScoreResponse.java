package com.accsaber.backend.model.dto.response.songsuggest;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SongSuggestScoreResponse {

    String songID;
    float pp;
    int rank;
}
