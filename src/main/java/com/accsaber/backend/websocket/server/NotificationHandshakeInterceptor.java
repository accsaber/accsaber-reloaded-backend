package com.accsaber.backend.websocket.server;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;

import com.accsaber.backend.security.PlayerTokenResolver;

@Component
public class NotificationHandshakeInterceptor extends PlayerHandshakeInterceptor {

    public NotificationHandshakeInterceptor(PlayerTokenResolver playerTokenResolver) {
        super(playerTokenResolver);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        PlayerTokenResolver.Resolution resolution = authenticate(queryParams(request.getURI()), response);
        if (!resolution.accepted()) {
            return false;
        }
        attributes.put(ATTR_USER_ID, resolution.user().getId());
        return true;
    }
}
