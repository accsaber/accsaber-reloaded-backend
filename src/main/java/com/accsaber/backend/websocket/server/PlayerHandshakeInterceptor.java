package com.accsaber.backend.websocket.server;

import java.net.URI;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.accsaber.backend.security.PlayerTokenResolver;

public abstract class PlayerHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "userId";

    private final PlayerTokenResolver playerTokenResolver;

    protected PlayerHandshakeInterceptor(PlayerTokenResolver playerTokenResolver) {
        this.playerTokenResolver = playerTokenResolver;
    }

    static MultiValueMap<String, String> queryParams(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams();
    }

    PlayerTokenResolver.Resolution authenticate(MultiValueMap<String, String> params,
            ServerHttpResponse response) {
        PlayerTokenResolver.Resolution resolution = playerTokenResolver.resolve(params.getFirst("token"));
        if (!resolution.accepted()) {
            response.setStatusCode(resolution.rejection());
        }
        return resolution;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }
}
