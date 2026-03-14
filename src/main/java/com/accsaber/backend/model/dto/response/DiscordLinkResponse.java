package com.accsaber.backend.model.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DiscordLinkResponse {

    String discordId;
    Long userId;
    String playerName;
    Instant createdAt;
}
