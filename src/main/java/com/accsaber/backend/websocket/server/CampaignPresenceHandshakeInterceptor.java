package com.accsaber.backend.websocket.server;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;

import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.security.PlayerTokenResolver;
import com.accsaber.backend.service.campaign.CampaignCollaboratorService;
import com.accsaber.backend.util.Slugs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignPresenceHandshakeInterceptor extends PlayerHandshakeInterceptor {

    static final String ATTR_CAMPAIGN_ID = "campaignId";
    static final String ATTR_USER_ID = "userId";
    static final String ATTR_USER_NAME = "userName";
    static final String ATTR_USER_AVATAR = "userAvatar";

    private final PlayerTokenResolver playerTokenResolver;
    private final CampaignCollaboratorService collaboratorService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        MultiValueMap<String, String> params = queryParams(request.getURI());
        UUID campaignId = Slugs.uuidOrNull(params.getFirst("campaignId"));
        if (campaignId == null) {
            log.warn("Presence handshake rejected: missing or invalid campaignId '{}'", params.getFirst("campaignId"));
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        PlayerTokenResolver.Resolution resolution = authenticate(playerTokenResolver, params, response);
        if (!resolution.accepted()) {
            log.warn("Presence handshake rejected: {} (campaign {})", resolution.reason(), campaignId);
            return false;
        }
        User user = resolution.user();
        if (!collaboratorService.isParticipant(campaignId, user.getId())) {
            log.warn("Presence handshake rejected: user {} is not owner or accepted collaborator of campaign {}",
                    user.getId(), campaignId);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put(ATTR_CAMPAIGN_ID, campaignId);
        attributes.put(ATTR_USER_ID, user.getId());
        attributes.put(ATTR_USER_NAME, user.getName());
        attributes.put(ATTR_USER_AVATAR,
                user.getCdnAvatarUrl() != null ? user.getCdnAvatarUrl() : user.getAvatarUrl());
        log.debug("Presence handshake accepted: user {} on campaign {}", user.getId(), campaignId);
        return true;
    }
}
