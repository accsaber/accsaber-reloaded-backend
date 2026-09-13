package com.accsaber.backend.model.event;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.service.chat.ChatChannel;

public record ChatMessageEvent(ChatChannel channel, UUID channelId, ChatMessageResponse message) {
}
