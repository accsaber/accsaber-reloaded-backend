package com.accsaber.backend.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.model.dto.response.clan.ClanFeedBroadcast;
import com.accsaber.backend.model.dto.response.clan.ClanFeedType;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.dto.response.item.CrateOpenBroadcast;
import com.accsaber.backend.model.dto.response.item.CrateOpenResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.event.ChatMessageEvent;
import com.accsaber.backend.model.event.ClanFeedEvent;
import com.accsaber.backend.model.event.CrateOpenedEvent;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.service.chat.ChatChannel;
import com.accsaber.backend.websocket.server.ClanFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.ClanGlobalFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.CrateFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.MarketFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.MilestoneFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.MissionFeedWebSocketHandler;
import com.accsaber.backend.websocket.server.NotificationWebSocketHandler;
import com.accsaber.backend.websocket.server.ScoreFeedWebSocketHandler;

@ExtendWith(MockitoExtension.class)
class FeedBroadcastServiceTest {

    private static final Long USER_ID = 76561198000000000L;

    @Mock
    private ScoreFeedWebSocketHandler scoreFeedHandler;
    @Mock
    private CrateFeedWebSocketHandler crateFeedHandler;
    @Mock
    private MarketFeedWebSocketHandler marketFeedHandler;
    @Mock
    private MilestoneFeedWebSocketHandler milestoneFeedHandler;
    @Mock
    private MissionFeedWebSocketHandler missionFeedHandler;
    @Mock
    private NotificationWebSocketHandler notificationHandler;
    @Mock
    private ClanFeedWebSocketHandler clanFeedHandler;
    @Mock
    private ClanGlobalFeedWebSocketHandler clanGlobalFeedHandler;
    @Mock
    private ChatChannel channel;

    @InjectMocks
    private FeedBroadcastService service;

    private ScoreResponse buildScoreResponse() {
        return ScoreResponse.builder()
                .id(UUID.randomUUID())
                .userId("76561198000000001")
                .mapDifficultyId(UUID.randomUUID())
                .score(950_000)
                .ap(500.000000)
                .weightedAp(500.000000)
                .modifierIds(Collections.emptyList())
                .active(true)
                .build();
    }

    @Test
    void scoreIsSerializedAndBroadcast() {
        ScoreResponse score = buildScoreResponse();

        service.onScoreSubmitted(new ScoreSubmittedEvent(score));

        verify(scoreFeedHandler).broadcast(argThat(json -> {
            assertThat(json).contains("\"userId\":\"" + score.getUserId() + "\"");
            assertThat(json).contains("\"score\":950000");
            return true;
        }));
    }

    @Test
    void partialScoresAreSkipped() {
        service.onScoreSubmitted(new ScoreSubmittedEvent(buildScoreResponse().toBuilder().partial(true).build()));

        verifyNoInteractions(scoreFeedHandler);
    }

    @Test
    void inactiveScoresAreSkipped() {
        service.onScoreSubmitted(new ScoreSubmittedEvent(buildScoreResponse().toBuilder().active(false).build()));

        verifyNoInteractions(scoreFeedHandler);
    }

    @Test
    void crateBroadcastEmbedsPlayerCrateAndFullRewardDtos() {
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
                "https://cdn.accsaber/a.webp", "us", null, null);

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

    @Test
    void chatBroadcastsTypedEnvelopeWithIsoTimestampThroughItsChannel() {
        UUID channelId = UUID.randomUUID();
        ChatMessageResponse message = new ChatMessageResponse(UUID.randomUUID(),
                new PlayerRef("76561198000000000", "Tester", null, null, "us", null, null),
                "hello team", null, null, null, null, Instant.parse("2026-07-03T21:00:00Z"));

        service.onChatMessage(new ChatMessageEvent(channelId, message, json -> channel.broadcast(channelId, json)));

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

    @Test
    void theSameClanPayloadGoesToEveryClanRoomWithWholeNumbersKeptWhole() {
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        UUID warId = UUID.randomUUID();

        service.onClanFeed(new ClanFeedEvent(List.of(attacker, defender),
                ClanFeedBroadcast.hit(warId, Map.of("damage", 25.0))));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(clanFeedHandler).broadcast(eq(attacker), json.capture());
        verify(clanFeedHandler).broadcast(defender, json.getValue());
        assertThat(json.getValue())
                .isEqualTo("{\"type\":\"hit\",\"warId\":\"" + warId + "\",\"data\":{\"damage\":25}}");
    }

    @Test
    void aClanFeedWithNoClansGoesOnlyToTheGlobalFeed() {
        service.onClanFeed(new ClanFeedEvent(List.of(),
                new ClanFeedBroadcast(ClanFeedType.rival_declared, null, List.of())));

        verify(clanGlobalFeedHandler).broadcast("{\"type\":\"rival_declared\",\"warId\":null,\"data\":[]}");
        verifyNoInteractions(clanFeedHandler);
    }

    @Test
    void aClanRoomFeedNeverReachesTheGlobalFeed() {
        service.onClanFeed(new ClanFeedEvent(List.of(UUID.randomUUID()), ClanFeedBroadcast.hit(UUID.randomUUID(), 1)));

        verify(clanFeedHandler).broadcast(any(), any());
        verifyNoInteractions(clanGlobalFeedHandler);
    }
}
