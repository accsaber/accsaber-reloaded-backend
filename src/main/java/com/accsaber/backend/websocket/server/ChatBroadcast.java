package com.accsaber.backend.websocket.server;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;

public record ChatBroadcast(String type, UUID channelId, ChatMessageResponse message) {

    public static ChatBroadcast of(UUID channelId, ChatMessageResponse message) {
        return new ChatBroadcast("chat", channelId, message);
    }
}
