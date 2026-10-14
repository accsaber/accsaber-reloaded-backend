package com.accsaber.backend.websocket.server;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.accsaber.backend.security.PlayerTokenResolver;
import com.accsaber.backend.util.Slugs;

public abstract class PlayerHandshakeInterceptor implements HandshakeInterceptor {

    static MultiValueMap<String, String> queryParams(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams();
    }

    static UUID uuidParam(URI uri, String name) {
        return uri == null ? null : Slugs.uuidOrNull(queryParams(uri).getFirst(name));
    }

    static PlayerTokenResolver.Resolution authenticate(PlayerTokenResolver resolver,
            MultiValueMap<String, String> params, ServerHttpResponse response) {
        PlayerTokenResolver.Resolution resolution = resolver.resolve(params.getFirst("token"));
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
