package com.accsaber.backend.websocket.server;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import com.accsaber.backend.model.event.PresenceChangedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends RoomWebSocketHandler<Long> {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    protected Long resolveRoomKey(WebSocketSession session) {
        return (Long) session.getAttributes().get(NotificationHandshakeInterceptor.ATTR_USER_ID);
    }

    @Override
    protected void onRoomOpened(Long userId) {
        eventPublisher.publishEvent(new PresenceChangedEvent(userId, true));
    }

    @Override
    protected void onRoomEmptied(Long userId) {
        eventPublisher.publishEvent(new PresenceChangedEvent(userId, false));
    }

    public void sendToUser(Long userId, String json) {
        sendToRoom(userId, json);
    }

    public Set<Long> onlineAmong(Collection<Long> userIds) {
        return userIds.stream().filter(this::hasSessions).collect(Collectors.toSet());
    }
}
