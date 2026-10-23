package com.accsaber.backend.service.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ChatMessageEvent;
import com.accsaber.backend.repository.chat.ChatMessageRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatMessageRepository chatRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final ChatRateLimitService chatRateLimitService;
    private final ApplicationEventPublisher eventPublisher;

    public Long requireParticipant(ChatChannel channel, UUID channelId, Long playerId) {
        Long userId = duplicateUserService.resolvePrimaryUserId(playerId);
        channel.assertParticipant(channelId, userId);
        return userId;
    }

    public Page<ChatMessageResponse> getMessages(ChatChannel channel, UUID channelId, Long playerId,
            Pageable pageable) {
        requireParticipant(channel, channelId, playerId);
        return getTranscript(channel, channelId, pageable);
    }

    public Page<ChatMessageResponse> getTranscript(ChatChannel channel, UUID channelId, Pageable pageable) {
        return responses(channel, channel.findMessages(channelId, pageable));
    }

    public Page<ChatMessageResponse> responses(ChatChannel channel, Page<ChatMessage> page) {
        return new PageImpl<>(channel.toResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    @Transactional
    public ChatMessageResponse sendMessage(ChatChannel channel, UUID channelId, Long playerId, String content) {
        Long userId = requireParticipant(channel, channelId, playerId);
        if (!chatRateLimitService.tryAcquire(userId)) {
            throw new TooManyRequestsException("You're sending messages too quickly. Please slow down.");
        }
        User author = userRepository.requireActive(userId);
        return post(channel, channelId, channel.newMessage(channelId, author, content.trim()));
    }

    @Transactional
    public ChatMessageResponse post(ChatChannel channel, UUID channelId, ChatMessage message) {
        ChatMessageResponse response = channel.toResponses(List.of(chatRepository.save(message))).getFirst();
        channel.prune(channelId);
        eventPublisher.publishEvent(new ChatMessageEvent(channelId, response,
                json -> channel.broadcast(channelId, json)));
        return response;
    }
}
