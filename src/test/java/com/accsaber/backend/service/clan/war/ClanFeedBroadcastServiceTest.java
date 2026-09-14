package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.event.ClanFeedEvent;
import com.accsaber.backend.websocket.server.ClanFeedBroadcast;
import com.accsaber.backend.websocket.server.ClanFeedWebSocketHandler;

@ExtendWith(MockitoExtension.class)
class ClanFeedBroadcastServiceTest {

    @Mock
    private ClanFeedWebSocketHandler feedHandler;

    @InjectMocks
    private ClanFeedBroadcastService service;

    @Test
    void theSamePayloadGoesToEveryClanRoomWithWholeNumbersKeptWhole() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID warId = UUID.randomUUID();

        service.onFeed(new ClanFeedEvent(List.of(attacker, defender),
                ClanFeedBroadcast.hit(warId, Map.of("damage", 25.0))));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(feedHandler).broadcast(eq(attacker), json.capture());
        verify(feedHandler).broadcast(defender, json.getValue());
        assertThat(json.getValue())
                .isEqualTo("{\"type\":\"hit\",\"warId\":\"" + warId + "\",\"data\":{\"damage\":25}}");
    }
}
