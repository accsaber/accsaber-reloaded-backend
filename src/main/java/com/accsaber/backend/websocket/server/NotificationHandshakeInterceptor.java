package com.accsaber.backend.websocket.server;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;

import com.accsaber.backend.security.PlayerTokenResolver;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationHandshakeInterceptor extends PlayerHandshakeInterceptor {

    static final String ATTR_USER_ID = "userId";

    private final PlayerTokenResolver playerTokenResolver;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        PlayerTokenResolver.Resolution resolution = authenticate(playerTokenResolver,
                queryParams(request.getURI()), response);
        if (!resolution.accepted()) {
            return false;
        }
        attributes.put(ATTR_USER_ID, resolution.user().getId());
        return true;
    }
}
