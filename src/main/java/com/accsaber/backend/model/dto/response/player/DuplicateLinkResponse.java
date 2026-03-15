package com.accsaber.backend.model.dto.response.player;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DuplicateLinkResponse {

    UUID id;
    String secondaryUserId;
    String secondaryUserName;
    String primaryUserId;
    String primaryUserName;
    boolean merged;
    Instant mergedAt;
    String reason;
    Instant createdAt;
}
