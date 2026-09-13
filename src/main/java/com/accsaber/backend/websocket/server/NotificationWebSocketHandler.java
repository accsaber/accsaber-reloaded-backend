package com.accsaber.backend.websocket.server;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class NotificationWebSocketHandler extends RoomWebSocketHandler<Long> {

    @Override
    protected Long resolveRoomKey(WebSocketSession session) {
        return (Long) session.getAttributes().get(NotificationHandshakeInterceptor.ATTR_USER_ID);
    }

    public void sendToUser(Long userId, String json) {
        sendToRoom(userId, json);
    }

    public Set<Long> onlineAmong(Collection<Long> userIds) {
        return userIds.stream().filter(this::hasSessions).collect(Collectors.toSet());
    }
}
