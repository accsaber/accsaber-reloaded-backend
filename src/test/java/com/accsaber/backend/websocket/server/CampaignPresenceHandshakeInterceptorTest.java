package com.accsaber.backend.websocket.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.security.PlayerTokenResolver;
import com.accsaber.backend.service.campaign.CampaignCollaboratorService;

@ExtendWith(MockitoExtension.class)
class CampaignPresenceHandshakeInterceptorTest {

    private static final UUID CAMPAIGN = UUID.randomUUID();

    @Mock
    private PlayerTokenResolver playerTokenResolver;
    @Mock
    private CampaignCollaboratorService collaboratorService;

    @InjectMocks
    private CampaignPresenceHandshakeInterceptor interceptor;

    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;

    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        attributes = new HashMap<>();
    }

    private void uri(String query) {
        when(request.getURI()).thenReturn(URI.create("ws://host/ws/campaigns/presence?" + query));
    }

    private void resolves(long userId) {
        User user = User.builder().id(userId).name("u").cdnAvatarUrl("https://cdn/a.webp").build();
        when(playerTokenResolver.resolve("tok")).thenReturn(new PlayerTokenResolver.Resolution(user, null, null));
    }

    @Test
    void acceptsParticipantAndStampsTheSession() {
        uri("campaignId=" + CAMPAIGN + "&token=tok");
        resolves(5L);
        when(collaboratorService.isParticipant(CAMPAIGN, 5L)).thenReturn(true);

        boolean ok = interceptor.beforeHandshake(request, response, null, attributes);

        assertThat(ok).isTrue();
        assertThat(attributes)
                .containsEntry(CampaignPresenceHandshakeInterceptor.ATTR_USER_ID, 5L)
                .containsEntry(CampaignPresenceHandshakeInterceptor.ATTR_CAMPAIGN_ID, CAMPAIGN)
                .containsEntry(CampaignPresenceHandshakeInterceptor.ATTR_USER_AVATAR, "https://cdn/a.webp");
    }

    @Test
    void rejectsNonParticipant() {
        uri("campaignId=" + CAMPAIGN + "&token=tok");
        resolves(9L);
        when(collaboratorService.isParticipant(CAMPAIGN, 9L)).thenReturn(false);

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isFalse();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void passesTheTokenRejectionThrough() {
        uri("campaignId=" + CAMPAIGN + "&token=tok");
        when(playerTokenResolver.resolve("tok"))
                .thenReturn(new PlayerTokenResolver.Resolution(null, HttpStatus.UNAUTHORIZED, "non-player token"));

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(collaboratorService, never()).isParticipant(any(), any());
    }

    @Test
    void rejectsMissingOrInvalidCampaignBeforeTouchingTheToken() {
        uri("campaignId=not-a-uuid&token=tok");

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isFalse();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        verify(playerTokenResolver, never()).resolve(any());
    }
}
