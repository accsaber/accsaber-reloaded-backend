package com.accsaber.backend.websocket.server;

import java.net.URI;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

import com.accsaber.backend.util.Slugs;

@Component
public class MarketFeedWebSocketHandler extends RoomWebSocketHandler<UUID> {

    @Override
    protected UUID resolveRoomKey(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        return Slugs.uuidOrNull(UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("listingId"));
    }
}
