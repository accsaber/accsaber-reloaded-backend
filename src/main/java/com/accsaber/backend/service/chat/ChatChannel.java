package com.accsaber.backend.service.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarRefResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.service.clan.ClanRefCache;

public interface ChatChannel {

    void assertParticipant(UUID channelId, Long userId);

    Page<ChatMessage> findMessages(UUID channelId, Pageable pageable);

    ChatMessage newMessage(UUID channelId, User author, String content);

    List<ChatMessageResponse> toResponses(List<ChatMessage> messages);

    default void prune(UUID channelId) {
    }

    void broadcast(UUID channelId, String json);

    static ChatMessageResponse toResponse(ChatMessage message, PublicClanResponse clan) {
        return new ChatMessageResponse(message.getId(), ClanRefCache.playerRef(message.getUser()), message.getContent(),
                message.getEvent(), ClanRefCache.playerRef(message.getSubjectUser()), clan,
                ClanWarRefResponse.of(message.getWar()), message.getCreatedAt());
    }
}
