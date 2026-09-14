package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.chat.ChatMessageRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.service.chat.ChatService;
import com.accsaber.backend.websocket.server.ClanChatWebSocketHandler;

@ExtendWith(MockitoExtension.class)
class ClanChatChannelTest {

    @Mock
    private ChatMessageRepository chatRepository;
    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanCosmeticService cosmeticService;
    @Mock
    private ChatService chatService;
    @Mock
    private ClanChatWebSocketHandler chatHandler;

    @InjectMocks
    private ClanChatChannel channel;

    private final Clan owls = Clan.builder().id(UUID.randomUUID()).name("Night Owls").tag("NOW").build();
    private final Clan lapiz = Clan.builder().id(UUID.randomUUID()).name("El Lapiz").tag("LPZ").build();
    private final User founder = User.builder().id(1L).name("Founder").build();

    @Test
    void onlyOpenMembersGetIn() {
        doThrow(new ForbiddenException("no")).when(accessService).require(owls.getId(), 9L, ClanPermission.CHAT);

        assertThatThrownBy(() -> channel.assertParticipant(owls.getId(), 9L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anAnnouncementPostsAnEventRowWithItsSubjects() {
        channel.announce(owls, ChatNotice.ofClan(ChatEvent.alliance_formed, founder, lapiz));

        ArgumentCaptor<ChatMessage> posted = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatService).post(eq(channel), eq(owls.getId()), posted.capture());
        assertThat(posted.getValue().getClan()).isSameAs(owls);
        assertThat(posted.getValue().getUser()).isSameAs(founder);
        assertThat(posted.getValue().getEvent()).isEqualTo(ChatEvent.alliance_formed);
        assertThat(posted.getValue().getSubjectClan()).isSameAs(lapiz);
        assertThat(posted.getValue().getContent()).isNull();
    }

    @Test
    void responsesCarryTheOtherClanWithItsCosmetics() {
        ChatMessage event = ChatMessage.builder().id(UUID.randomUUID()).event(ChatEvent.rivaled_by).user(founder)
                .subjectClan(lapiz).build();
        ChatMessage said = ChatMessage.builder().id(UUID.randomUUID()).user(founder).content("gg").build();
        when(cosmeticService.publicRefs(anyCollection())).thenAnswer(inv -> inv.<Collection<Clan>>getArgument(0)
                .stream().collect(Collectors.toMap(Clan::getId,
                        clan -> PublicClanResponse.of(clan, List.of()))));

        var responses = channel.toResponses(List.of(event, said));

        assertThat(responses.get(0).clan().tag()).isEqualTo("LPZ");
        assertThat(responses.get(0).event()).isEqualTo(ChatEvent.rivaled_by);
        assertThat(responses.get(1).clan()).isNull();
        assertThat(responses.get(1).content()).isEqualTo("gg");
    }

    @Test
    void clanChatKeepsEveryMessageAndBroadcastsToTheClan() {
        channel.prune(owls.getId());
        channel.broadcast(owls.getId(), "{}");

        verifyNoInteractions(chatRepository);
        verify(chatHandler).broadcast(owls.getId(), "{}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMembershipChangeClosesSocketsOfPlayersNoLongerInTheClan() {
        when(memberRepository.findOpenUserIds(owls.getId())).thenReturn(List.of(1L));

        channel.onMembershipChanged(new ClanMembershipChangedEvent(owls.getId()));

        ArgumentCaptor<Supplier<Collection<Long>>> members = ArgumentCaptor.forClass(Supplier.class);
        verify(chatHandler).keepOnly(eq(owls.getId()), members.capture());
        assertThat(members.getValue().get()).containsExactly(1L);
    }
}
