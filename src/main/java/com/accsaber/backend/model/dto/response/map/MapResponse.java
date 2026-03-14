package com.accsaber.backend.model.dto.response.map;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MapResponse {

    UUID id;
    String songName;
    String songAuthor;
    String songHash;
    String mapAuthor;
    String beatsaverCode;
    String coverUrl;
    List<MapDifficultyResponse> difficulties;
    Instant createdAt;
}
