package com.accsaber.backend.service.chat;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
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

    private static final int MAX_MESSAGES_PER_CHANNEL = 1000;

    private final ChatMessageRepository chatRepository;
    private final UserRepository userRepository;
    private final DuplicateUserService duplicateUserService;
    private final ChatRateLimitService chatRateLimitService;
    private final ApplicationEventPublisher eventPublisher;

    public Page<ChatMessageResponse> getMessages(ChatChannel channel, UUID channelId, Long playerId,
            Pageable pageable) {
        Long userId = duplicateUserService.resolvePrimaryUserId(playerId);
        channel.assertParticipant(channelId, userId);
        return channel.findMessages(channelId, pageable).map(ChatMessageResponse::of);
    }

    @Transactional
    public ChatMessageResponse sendMessage(ChatChannel channel, UUID channelId, Long playerId, String content) {
        Long userId = duplicateUserService.resolvePrimaryUserId(playerId);
        channel.assertParticipant(channelId, userId);
        if (!chatRateLimitService.tryAcquire(userId)) {
            throw new TooManyRequestsException("You're sending messages too quickly. Please slow down.");
        }
        User author = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        ChatMessageResponse response = ChatMessageResponse.of(
                chatRepository.save(channel.newMessage(channelId, author, content.trim())));
        channel.prune(channelId, MAX_MESSAGES_PER_CHANNEL);
        eventPublisher.publishEvent(new ChatMessageEvent(channel, channelId, response));
        return response;
    }
}
