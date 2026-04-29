package com.accsaber.backend.model.dto.response.songsuggest;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SongSuggestRefreshTimeResponse {

    Instant refreshTime;
}
