package com.accsaber.backend.websocket.server;

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
}
