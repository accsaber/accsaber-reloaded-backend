package com.accsaber.backend.model.dto.response.chat;

import java.time.Instant;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.clan.ClanWarRefResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.chat.ChatMessage;

public record ChatMessageResponse(
        UUID id,
        PlayerRef author,
        String content,
        ChatEvent event,
        PlayerRef subject,
        PublicClanResponse clan,
        ClanWarRefResponse war,
        Instant createdAt) {

    public static ChatMessageResponse of(ChatMessage message, PublicClanResponse clan) {
        return new ChatMessageResponse(message.getId(), PlayerRef.of(message.getUser()), message.getContent(),
                message.getEvent(), PlayerRef.of(message.getSubjectUser()), clan,
                ClanWarRefResponse.of(message.getWar()), message.getCreatedAt());
    }
}
