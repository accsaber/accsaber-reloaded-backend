package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.dto.response.item.CrateOpenResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.event.CrateOpenedEvent;
import com.accsaber.backend.websocket.server.CrateFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.CrateOpenBroadcast;

@ExtendWith(MockitoExtension.class)
class CrateBroadcastServiceTest {

    @Mock
    private CrateFeedWebSocketHandler crateFeedHandler;

    @InjectMocks
    private CrateBroadcastService service;

    private static final Long USER_ID = 76561198000000000L;

    @Test
    void broadcastEmbedsPlayerCrateAndFullRewardDtos() {
        UserItemResponse reward = UserItemResponse.builder()
                .linkId(UUID.randomUUID())
                .item(ItemResponse.builder().id(UUID.randomUUID()).typeKey("saber")
                        .name("Ascendant Saber").rarity("legendary").worth(500L).build())
                .modifiers(List.of(UserItemResponse.ModifierRef.builder()
                        .id(UUID.randomUUID()).key("unusual").name("Unusual").colorHex("#8650AC").build()))
                .unusualEffect(UserItemResponse.EffectRef.builder()
                        .id(UUID.randomUUID()).key("fiery").name("Fiery").build())
                .serialNumber(7L)
                .quantity(1L)
                .build();

        CrateOpenResponse open = CrateOpenResponse.builder()
                .id(UUID.randomUUID())
                .crate(ItemResponse.builder().id(UUID.randomUUID()).typeKey("crate").name("Alpha Crate")
                        .rarity("rare").build())
                .consumedLinkId(UUID.randomUUID())
                .reward(reward)
                .rolledAt(Instant.parse("2026-07-25T18:00:00Z"))
                .build();

        PlayerRef player = new PlayerRef(String.valueOf(USER_ID), "Tikugato", "https://cdn.example/a.png",
                "https://cdn.accsaber/a.webp", "us");

        service.onCrateOpened(new CrateOpenedEvent(new CrateOpenBroadcast("crate_opened", player, open)));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(crateFeedHandler).broadcast(json.capture());
        assertThat(json.getValue())
                .contains("\"type\":\"crate_opened\"")
                .contains("\"name\":\"Tikugato\"")
                .contains("\"cdnAvatarUrl\":\"https://cdn.accsaber/a.webp\"")
                .contains("\"id\":\"" + USER_ID + "\"")
                .doesNotContain("\"id\":" + USER_ID)
                .contains("\"name\":\"Alpha Crate\"")
                .contains("\"name\":\"Ascendant Saber\"")
                .contains("\"key\":\"unusual\"")
                .contains("\"key\":\"fiery\"")
                .contains("\"serialNumber\":7")
                .contains("2026-07-25T18:00:00Z");
    }
}
