package com.accsaber.backend.model.dto.response.map;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublicMapResponse {

    UUID id;
    String songName;
    String songSubName;
    String songAuthor;
    String songHash;
    String mapAuthor;
    String beatsaverCode;
    String coverUrl;
    List<PublicMapDifficultyResponse> difficulties;
    Instant createdAt;
}
