package com.accsaber.backend.model.event;

import java.util.List;
import java.util.UUID;

import com.accsaber.backend.websocket.server.ClanFeedBroadcast;

public record ClanFeedEvent(List<UUID> clanIds, ClanFeedBroadcast payload) {
}
