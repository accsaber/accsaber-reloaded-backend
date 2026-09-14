package com.accsaber.backend.service.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.user.User;

public interface ChatChannel {

    void assertParticipant(UUID channelId, Long userId);

    Page<ChatMessage> findMessages(UUID channelId, Pageable pageable);

    ChatMessage newMessage(UUID channelId, User author, String content);

    List<ChatMessageResponse> toResponses(List<ChatMessage> messages);

    default void prune(UUID channelId) {
    }

    void broadcast(UUID channelId, String json);
}
