package com.accsaber.backend.websocket.server;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;

public record ClanPresenceBroadcast(String type, UUID channelId, PlayerRef player, boolean online) {

    public static ClanPresenceBroadcast of(UUID clanId, PlayerRef player, boolean online) {
        return new ClanPresenceBroadcast("presence", clanId, player, online);
    }
}
