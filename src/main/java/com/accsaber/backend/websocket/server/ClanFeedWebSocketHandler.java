package com.accsaber.backend.websocket.server;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

import com.accsaber.backend.util.Slugs;

@Component
public class ClanFeedWebSocketHandler extends RoomWebSocketHandler<UUID> {

    @Override
    protected UUID resolveRoomKey(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        return Slugs.uuidOrNull(UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("clanId"));
    }
}
