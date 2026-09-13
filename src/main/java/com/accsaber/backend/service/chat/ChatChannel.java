package com.accsaber.backend.service.chat;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.user.User;

public interface ChatChannel {

    void assertParticipant(UUID channelId, Long userId);

    Page<ChatMessage> findMessages(UUID channelId, Pageable pageable);

    ChatMessage newMessage(UUID channelId, User author, String content);

    void prune(UUID channelId, int keep);

    void broadcast(UUID channelId, String json);
}
