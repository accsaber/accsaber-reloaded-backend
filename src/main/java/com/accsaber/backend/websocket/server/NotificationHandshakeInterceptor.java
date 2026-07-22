package com.accsaber.backend.websocket.server;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.staff.JwtService;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "userId";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams();
        String token = params.getFirst("token");
        if (token == null || token.isBlank()) {
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

        attributes.put(ATTR_USER_ID, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }
}
