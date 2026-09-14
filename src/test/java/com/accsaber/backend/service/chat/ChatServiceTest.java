package com.accsaber.backend.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ChatMessageEvent;
import com.accsaber.backend.repository.chat.ChatMessageRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository chatRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private ChatRateLimitService chatRateLimitService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ChatChannel channel;

    @InjectMocks
    private ChatService service;

    private UUID channelId;
    private User user;

    @BeforeEach
    void setUp() {
        channelId = UUID.randomUUID();
        user = User.builder().id(50L).name("Tester").build();
        lenient().when(duplicateUserService.resolvePrimaryUserId(51L)).thenReturn(50L);
    }

    @Test
    void participantMessageIsTrimmedSavedPrunedAndPublishedToItsChannel() {
        when(userRepository.findByIdAndActiveTrue(50L)).thenReturn(Optional.of(user));
        when(chatRateLimitService.tryAcquire(50L)).thenReturn(true);
        when(channel.newMessage(eq(channelId), eq(user), any()))
                .thenAnswer(inv -> ChatMessage.builder().user(user).content(inv.getArgument(2)).build());
        when(chatRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        mapsPlainly();

        var response = service.sendMessage(channel, channelId, 51L, "  hello team  ");

        verify(channel).assertParticipant(channelId, 50L);
        verify(channel).newMessage(channelId, user, "hello team");
        verify(channel).prune(channelId);
        ArgumentCaptor<ChatMessageEvent> event = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().channel()).isSameAs(channel);
        assertThat(event.getValue().channelId()).isEqualTo(channelId);
        assertThat(response.content()).isEqualTo("hello team");
        assertThat(response.author().id()).isEqualTo("50");
        assertThat(response.author().name()).isEqualTo("Tester");
    }

    private void mapsPlainly() {
        when(channel.toResponses(any())).thenAnswer(inv -> inv.<List<ChatMessage>>getArgument(0).stream()
                .map(m -> ChatMessageResponse.of(m, null)).toList());
    }

    @Test
    void anAnnouncementIsSavedPrunedAndPublishedWithoutARateLimit() {
        ChatMessage notice = ChatMessage.builder().event(ChatEvent.member_joined).user(user).build();
        when(chatRepository.save(notice)).thenReturn(notice);
        mapsPlainly();

        var response = service.post(channel, channelId, notice);

        assertThat(response.event()).isEqualTo(ChatEvent.member_joined);
        verify(channel).prune(channelId);
        verify(eventPublisher).publishEvent(any(ChatMessageEvent.class));
        verify(chatRateLimitService, never()).tryAcquire(any());
    }

    @Test
    void throttledSenderIsRejectedBeforePersisting() {
        when(chatRateLimitService.tryAcquire(50L)).thenReturn(false);

        assertThatThrownBy(() -> service.sendMessage(channel, channelId, 51L, "spam"))
                .isInstanceOf(TooManyRequestsException.class);

        verify(chatRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void nonParticipantIsRejectedBeforeAnythingElse() {
        doThrow(new ValidationException("no")).when(channel).assertParticipant(channelId, 50L);

        assertThatThrownBy(() -> service.sendMessage(channel, channelId, 51L, "let me in"))
                .isInstanceOf(ValidationException.class);

        verify(chatRateLimitService, never()).tryAcquire(any());
        verify(chatRepository, never()).save(any());
    }

    @Test
    void historyIsCheckedThenMappedFromTheChannel() {
        ChatMessage message = ChatMessage.builder().id(UUID.randomUUID()).user(user).content("hi").build();
        when(channel.findMessages(eq(channelId), any())).thenReturn(new PageImpl<>(List.of(message)));
        mapsPlainly();

        var page = service.getMessages(channel, channelId, 51L, PageRequest.of(0, 50));

        verify(channel).assertParticipant(channelId, 50L);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).content()).isEqualTo("hi");
        assertThat(page.getContent().get(0).author().name()).isEqualTo("Tester");
    }
}
