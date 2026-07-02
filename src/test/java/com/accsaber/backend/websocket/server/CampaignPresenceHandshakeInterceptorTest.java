package com.accsaber.backend.websocket.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.staff.JwtService;

@ExtendWith(MockitoExtension.class)
class CampaignPresenceHandshakeInterceptorTest {

    private static final UUID CAMPAIGN = UUID.randomUUID();

    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignCollaboratorRepository collaboratorRepository;

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

    private void validPlayerToken(long userId) {
        when(jwtService.extractTokenType("tok")).thenReturn(JwtService.TYPE_PLAYER);
        when(jwtService.extractPlayerId("tok")).thenReturn(userId);
        when(duplicateUserService.resolvePrimaryUserId(userId)).thenReturn(userId);
        when(userRepository.findByIdAndActiveTrue(userId))
                .thenReturn(Optional.of(User.builder().id(userId).name("u").active(true).banned(false).build()));
    }

    @Test
    void acceptsOwner() {
        uri("campaignId=" + CAMPAIGN + "&token=tok");
        validPlayerToken(5L);
        when(campaignRepository.findCreatorIdByIdAndActiveTrue(CAMPAIGN)).thenReturn(Optional.of(5L));

        boolean ok = interceptor.beforeHandshake(request, response, null, attributes);

        assertThat(ok).isTrue();
        assertThat(attributes).containsEntry(CampaignPresenceHandshakeInterceptor.ATTR_USER_ID, 5L);
        assertThat(attributes).containsEntry(CampaignPresenceHandshakeInterceptor.ATTR_CAMPAIGN_ID, CAMPAIGN);
    }

    @Test
    void acceptsAcceptedCollaborator() {
        uri("campaignId=" + CAMPAIGN + "&token=tok");
        validPlayerToken(7L);
        when(campaignRepository.findCreatorIdByIdAndActiveTrue(CAMPAIGN)).thenReturn(Optional.of(5L));
        when(collaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                CAMPAIGN, 7L, CampaignCollaboratorStatus.ACCEPTED)).thenReturn(true);

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isTrue();
    }

    @Test
    void rejectsNonMember() {
        uri("campaignId=" + CAMPAIGN + "&token=tok");
        validPlayerToken(9L);
        when(campaignRepository.findCreatorIdByIdAndActiveTrue(CAMPAIGN)).thenReturn(Optional.of(5L));
        when(collaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                CAMPAIGN, 9L, CampaignCollaboratorStatus.ACCEPTED)).thenReturn(false);

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isFalse();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsBannedUser() {
        uri("campaignId=" + CAMPAIGN + "&token=tok");
        when(jwtService.extractTokenType("tok")).thenReturn(JwtService.TYPE_PLAYER);
        when(jwtService.extractPlayerId("tok")).thenReturn(5L);
        when(duplicateUserService.resolvePrimaryUserId(5L)).thenReturn(5L);
        when(userRepository.findByIdAndActiveTrue(5L))
                .thenReturn(Optional.of(User.builder().id(5L).name("u").active(true).banned(true).build()));

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isFalse();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsMissingToken() {
        uri("campaignId=" + CAMPAIGN);

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isFalse();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsNonPlayerToken() {
        uri("campaignId=" + CAMPAIGN + "&token=tok");
        when(jwtService.extractTokenType("tok")).thenReturn(JwtService.TYPE_STAFF);

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
