package com.accsaber.backend.websocket.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.security.PlayerTokenResolver;

@ExtendWith(MockitoExtension.class)
class ClanChatHandshakeInterceptorTest {

    private static final UUID CLAN = UUID.randomUUID();

    @Mock
    private PlayerTokenResolver playerTokenResolver;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;

    @InjectMocks
    private ClanChatHandshakeInterceptor interceptor;

    private final Map<String, Object> attributes = new HashMap<>();

    private void uri(String query) {
        when(request.getURI()).thenReturn(URI.create("ws://host/ws/clans/chat?" + query));
    }

    private void player(long userId) {
        when(playerTokenResolver.resolve("tok"))
                .thenReturn(new PlayerTokenResolver.Resolution(User.builder().id(userId).build(), null, null));
    }

    @Test
    void aMemberJoinsTheirClansRoom() {
        uri("clanId=" + CLAN + "&token=tok");
        player(5L);
        when(memberRepository.findOpenByClanIdAndUserId(CLAN, 5L)).thenReturn(Optional.of(ClanMember.builder().build()));

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isTrue();
        assertThat(attributes).containsEntry(ClanChatHandshakeInterceptor.ATTR_CLAN_ID, CLAN)
                .containsEntry(ClanChatHandshakeInterceptor.ATTR_USER_ID, 5L);
    }

    @Test
    void someoneOutsideTheClanIsForbidden() {
        uri("clanId=" + CLAN + "&token=tok");
        player(9L);
        when(memberRepository.findOpenByClanIdAndUserId(CLAN, 9L)).thenReturn(Optional.empty());

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isFalse();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void aRejectedTokenPassesItsStatusOn() {
        uri("clanId=" + CLAN + "&token=tok");
        when(playerTokenResolver.resolve("tok"))
                .thenReturn(new PlayerTokenResolver.Resolution(null, HttpStatus.UNAUTHORIZED, "invalid"));

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aMissingOrBrokenClanIdIsABadRequest() {
        uri("clanId=nope&token=tok");

        assertThat(interceptor.beforeHandshake(request, response, null, attributes)).isFalse();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
    }
}
