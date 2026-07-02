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

import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.staff.JwtService;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CampaignPresenceHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_CAMPAIGN_ID = "campaignId";
    static final String ATTR_USER_ID = "userId";
    static final String ATTR_USER_NAME = "userName";
    static final String ATTR_USER_AVATAR = "userAvatar";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final CampaignRepository campaignRepository;
    private final CampaignCollaboratorRepository collaboratorRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams();
        String token = params.getFirst("token");
        String campaignParam = params.getFirst("campaignId");
        if (token == null || token.isBlank() || campaignParam == null || campaignParam.isBlank()) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        UUID campaignId;
        try {
            campaignId = UUID.fromString(campaignParam);
        } catch (IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        Long userId;
        try {
            jwtService.validateToken(token);
            if (!JwtService.TYPE_PLAYER.equals(jwtService.extractTokenType(token))) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            userId = duplicateUserService.resolvePrimaryUserId(jwtService.extractPlayerId(token));
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        User user = userRepository.findByIdAndActiveTrue(userId).orElse(null);
        if (user == null || user.isBanned()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        if (!isMember(campaignId, userId)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put(ATTR_CAMPAIGN_ID, campaignId);
        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_USER_NAME, user.getName());
        attributes.put(ATTR_USER_AVATAR,
                user.getCdnAvatarUrl() != null ? user.getCdnAvatarUrl() : user.getAvatarUrl());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }

    private boolean isMember(UUID campaignId, Long userId) {
        Long creatorId = campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId).orElse(null);
        if (userId.equals(creatorId)) {
            return true;
        }
        return collaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                campaignId, userId, CampaignCollaboratorStatus.ACCEPTED);
    }
}
