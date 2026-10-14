package com.accsaber.backend.websocket.server;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;

import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.security.PlayerTokenResolver;
import com.accsaber.backend.util.Slugs;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClanChatHandshakeInterceptor extends PlayerHandshakeInterceptor {

    static final String ATTR_CLAN_ID = "clanId";
    static final String ATTR_USER_ID = "userId";

    private final PlayerTokenResolver playerTokenResolver;
    private final ClanMemberRepository memberRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        MultiValueMap<String, String> params = queryParams(request.getURI());
        UUID clanId = Slugs.uuidOrNull(params.getFirst("clanId"));
        if (clanId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        PlayerTokenResolver.Resolution resolution = authenticate(playerTokenResolver, params, response);
        if (!resolution.accepted()) {
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
}
