package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.PresenceChangedEvent;
import com.accsaber.backend.repository.clan.ClanEquippedItemRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.websocket.server.ClanChatWebSocketHandler;
import com.accsaber.backend.websocket.server.NotificationWebSocketHandler;

@ExtendWith(MockitoExtension.class)
class ClanPresenceServiceTest {

    private static final Long MEMBER = 7L;
    private static final Long CLANLESS = 8L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationWebSocketHandler notificationHandler;
    @Mock
    private ClanChatWebSocketHandler chatHandler;

    private final Clan owls = Clan.builder().id(new UUID(1L, 1L)).name("Night Owls").tag("NOW").build();
    private final ClanMemberRepository memberRepository = mock(ClanMemberRepository.class);
    private final ClanEquippedItemRepository equippedRepository = mock(ClanEquippedItemRepository.class);
    private final ClanRefCache cache = new ClanRefCache(mock(ClanRepository.class), memberRepository,
            equippedRepository);
    private ClanPresenceService service;

    @BeforeEach
    void setUp() {
        when(memberRepository.findAllOpenInActiveClans()).thenReturn(List.of(
                ClanMember.builder().clan(owls).user(User.builder().id(MEMBER).build()).build()));
        cache.reload();
        service = new ClanPresenceService(userRepository, notificationHandler, chatHandler);
        lenient().when(userRepository.findByIdAndActiveTrue(MEMBER))
                .thenReturn(Optional.of(User.builder().id(MEMBER).name("Owl").build()));
    }

    @AfterEach
    void emptyTheCache() {
        when(memberRepository.findAllOpenInActiveClans()).thenReturn(List.of());
        cache.reload();
    }

    @Test
    void comingOnlineIsAnnouncedInTheClanChat() {
        service.onPresenceChanged(new PresenceChangedEvent(MEMBER, true));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(chatHandler).broadcast(eq(owls.getId()), json.capture());
        assertThat(json.getValue()).contains("\"type\":\"presence\"", "\"online\":true", "\"name\":\"Owl\"",
                "\"channelId\":\"" + owls.getId() + "\"");
    }

    @Test
    void aClanlessPlayerIsNeverLookedUp() {
        service.onPresenceChanged(new PresenceChangedEvent(CLANLESS, true));

        verify(userRepository, never()).findByIdAndActiveTrue(any());
        verify(chatHandler, never()).broadcast(any(), any());
    }

    @Test
    void aReloadInsideTheGraceWindowSaysNothingAtAll() {
        service.onPresenceChanged(new PresenceChangedEvent(MEMBER, false));
        service.onPresenceChanged(new PresenceChangedEvent(MEMBER, true));
        service.flushOffline(Instant.now().plus(ClanPresenceService.OFFLINE_GRACE).plusSeconds(1));

        verify(chatHandler, never()).broadcast(any(), any());
    }

    @Test
    void goingOfflineIsAnnouncedOnceTheGraceWindowPasses() {
        when(notificationHandler.onlineAmong(List.of(MEMBER))).thenReturn(Set.of());
        service.onPresenceChanged(new PresenceChangedEvent(MEMBER, false));

        service.flushOffline(Instant.now());
        verify(chatHandler, never()).broadcast(any(), any());

        service.flushOffline(Instant.now().plus(ClanPresenceService.OFFLINE_GRACE).plusSeconds(1));
        service.flushOffline(Instant.now().plus(ClanPresenceService.OFFLINE_GRACE).plusSeconds(10));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(chatHandler).broadcast(eq(owls.getId()), json.capture());
        assertThat(json.getValue()).contains("\"online\":false");
    }
}
