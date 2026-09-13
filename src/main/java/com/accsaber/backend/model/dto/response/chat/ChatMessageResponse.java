package com.accsaber.backend.model.dto.response.chat;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.chat.ChatMessage;

public record ChatMessageResponse(UUID id, PlayerRef author, String content, Instant createdAt) {

    public static ChatMessageResponse of(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), PlayerRef.of(message.getUser()), message.getContent(),
                message.getCreatedAt());
    }
}
