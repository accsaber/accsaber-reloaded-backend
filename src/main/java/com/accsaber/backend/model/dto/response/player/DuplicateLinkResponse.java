package com.accsaber.backend.model.dto.response.player;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DuplicateLinkResponse {

    UUID id;
    PlayerRef secondary;
    PlayerRef primary;
    boolean merged;
    Instant mergedAt;
    String reason;
    Instant createdAt;
}
