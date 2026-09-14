package com.accsaber.backend.websocket.server;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.security.PlayerTokenResolver;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClanChatHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_CLAN_ID = "clanId";
    static final String ATTR_USER_ID = "userId";

    private final PlayerTokenResolver playerTokenResolver;
    private final ClanMemberRepository memberRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        UUID clanId = parseUuid(params.getFirst("clanId"));
        if (clanId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        PlayerTokenResolver.Resolution resolution = playerTokenResolver.resolve(params.getFirst("token"));
        if (!resolution.accepted()) {
            response.setStatusCode(resolution.rejection());
            return false;
        }
        Long userId = resolution.user().getId();
        if (memberRepository.findOpenByClanIdAndUserId(clanId, userId).isEmpty()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ATTR_CLAN_ID, clanId);
        attributes.put(ATTR_USER_ID, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
