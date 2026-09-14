package com.accsaber.backend.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.event.ChatMessageEvent;

@ExtendWith(MockitoExtension.class)
class ChatBroadcastServiceTest {

    @Mock
    private ChatChannel channel;

    private final ChatBroadcastService service = new ChatBroadcastService();

    @Test
    void broadcastsTypedChatEnvelopeWithIsoTimestampThroughItsChannel() {
        UUID channelId = UUID.randomUUID();
        ChatMessageResponse message = new ChatMessageResponse(UUID.randomUUID(),
                new PlayerRef("76561198000000000", "Tester", null, null, "us", null),
                "hello team", null, null, null, null, Instant.parse("2026-07-03T21:00:00Z"));

        service.onChatMessage(new ChatMessageEvent(channel, channelId, message));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(channel).broadcast(eq(channelId), json.capture());
        assertThat(json.getValue())
                .contains("\"type\":\"chat\"")
                .contains("\"id\":\"76561198000000000\"")
                .contains("\"name\":\"Tester\"")
                .contains("hello team")
                .contains("2026-07-03T21:00:00Z")
                .contains(channelId.toString());
    }
}
