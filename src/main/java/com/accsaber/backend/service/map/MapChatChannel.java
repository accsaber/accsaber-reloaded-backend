package com.accsaber.backend.service.map;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.chat.ChatMessageRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.service.chat.ChatChannel;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MapChatChannel implements ChatChannel {

    private static final Duration CONVERSATION_COOLDOWN = Duration.ofMinutes(5);

    private final ChatMessageRepository chatRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapSubscriptionService subscriptionService;
    private final StaffUserRepository staffUserRepository;

    @Override
    public void assertParticipant(UUID difficultyId, Long userId) {
        mapDifficultyRepository.requireCommentable(difficultyId);
    }

    @Override
    public Page<ChatMessage> findMessages(UUID difficultyId, Pageable pageable) {
        mapDifficultyRepository.requireCommentable(difficultyId);
        return chatRepository.findByMapDifficulty_IdOrderByCreatedAtDesc(difficultyId, pageable);
    }

    @Override
    public ChatMessage newMessage(UUID difficultyId, User author, String content) {
        return ChatMessage.builder()
                .mapDifficulty(mapDifficultyRepository.getReferenceById(difficultyId))
                .user(author)
                .content(content)
                .build();
    }

    @Override
    public List<ChatMessageResponse> toResponses(List<ChatMessage> messages) {
        Set<Long> authors = messages.stream().map(message -> message.getUser().getId()).collect(Collectors.toSet());
        Set<Long> rankingTeam = authors.isEmpty() ? Set.of() : staffUserRepository.findRankingTeamUserIds(authors);
        return messages.stream().map(message -> ChatChannel.toResponse(message, null,
                rankingTeam.contains(message.getUser().getId()) ? Boolean.TRUE : null)).toList();
    }

    @Override
    public void posted(ChatMessage message) {
        UUID difficultyId = message.getMapDifficulty().getId();
        if (!chatRepository.existsByMapDifficulty_IdAndCreatedAtAfterAndIdNot(difficultyId,
                Instant.now().minus(CONVERSATION_COOLDOWN), message.getId())) {
            subscriptionService.commented(message.getMapDifficulty(), message.getUser());
        }
    }

    @Override
    public void broadcast(UUID difficultyId, String json) {
    }
}
