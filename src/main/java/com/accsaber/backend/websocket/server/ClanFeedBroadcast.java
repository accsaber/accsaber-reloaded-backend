package com.accsaber.backend.websocket.server;

import java.util.UUID;

public record ClanFeedBroadcast(ClanFeedType type, UUID warId, Object data) {

    public static ClanFeedBroadcast war(UUID warId, Object war) {
        return new ClanFeedBroadcast(ClanFeedType.war, warId, war);
    }

    public static ClanFeedBroadcast hit(UUID warId, Object hit) {
        return new ClanFeedBroadcast(ClanFeedType.hit, warId, hit);
    }
}
