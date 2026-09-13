package com.accsaber.backend.websocket.server;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.accsaber.backend.security.PlayerTokenResolver;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "userId";

    private final PlayerTokenResolver playerTokenResolver;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        PlayerTokenResolver.Resolution resolution = playerTokenResolver.resolve(token);
        if (!resolution.accepted()) {
            response.setStatusCode(resolution.rejection());
            return false;
        }
        attributes.put(ATTR_USER_ID, resolution.user().getId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }
}
