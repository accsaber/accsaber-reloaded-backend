package com.accsaber.backend.model.dto.response.chat;

import java.util.UUID;


public record ChatBroadcast(String type, UUID channelId, ChatMessageResponse message) {

    public static ChatBroadcast of(UUID channelId, ChatMessageResponse message) {
        return new ChatBroadcast("chat", channelId, message);
    }
}
