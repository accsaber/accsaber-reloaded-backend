package com.accsaber.backend.websocket.server;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ClanChatWebSocketHandler extends RoomWebSocketHandler<UUID> {

    @Override
    protected UUID resolveRoomKey(WebSocketSession session) {
        return (UUID) session.getAttributes().get(ClanChatHandshakeInterceptor.ATTR_CLAN_ID);
    }

    public void broadcast(UUID clanId, String json) {
        sendToRoom(clanId, json);
    }

    public void keepOnly(UUID clanId, Supplier<Collection<Long>> members) {
        if (!hasSessions(clanId)) {
            return;
        }
        Collection<Long> current = members.get();
        closeWhere(clanId, session -> !current.contains(
                (Long) session.getAttributes().get(ClanChatHandshakeInterceptor.ATTR_USER_ID)));
    }
}
