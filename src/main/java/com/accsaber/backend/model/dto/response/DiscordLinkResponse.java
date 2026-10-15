package com.accsaber.backend.model.dto.response;

import java.time.Instant;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DiscordLinkResponse {

    String discordId;
    PlayerRef player;
    Instant createdAt;
}
