package com.accsaber.backend.model.event;

import java.util.UUID;
import java.util.function.Consumer;

import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;

public record ChatMessageEvent(UUID channelId, ChatMessageResponse message, Consumer<String> sink) {
}
