package com.accsaber.backend.model.dto.response.map;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LeaderboardAliasResponse {

    UUID id;
    String ssLeaderboardId;
    String blLeaderboardId;
    Instant linkedAt;
}
