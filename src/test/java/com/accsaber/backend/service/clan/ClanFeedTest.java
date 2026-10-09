package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.accsaber.backend.model.dto.response.clan.ClanSeasonClosedResponse;
import com.accsaber.backend.model.dto.response.clan.ClanSeasonResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarHitResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarHit;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanFeedEvent;
import com.accsaber.backend.service.clan.war.ClanWarResponses;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.websocket.server.ClanFeedType;

@ExtendWith(MockitoExtension.class)
class ClanFeedTest {

    @Mock
    private ClanWarResponses warResponses;
    @Mock
    private ClanCosmeticService cosmeticService;
    @Mock
    private MapService mapService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ClanFeed feed;

    private final ClanWar war = ClanWar.builder().id(UUID.randomUUID())
            .attackerClan(Clan.builder().id(new UUID(1L, 1L)).build())
            .defenderClan(Clan.builder().id(new UUID(2L, 1L)).build()).build();

    private List<ClanFeedEvent> published(int count) {
        ArgumentCaptor<ClanFeedEvent> event = ArgumentCaptor.forClass(ClanFeedEvent.class);
        verify(eventPublisher, times(count)).publishEvent(event.capture());
        return event.getAllValues();
    }

    private ClanWarResponse warResponse() {
        ClanWarResponse response = new ClanWarResponse(war.getId(), null, null, null, null, null, null, null, null,
                null, null, null, null);
        when(warResponses.of(war)).thenReturn(response);
        return response;
    }

    private ClanWarHit hit(boolean broke) {
        UUID difficultyId = UUID.randomUUID();
        PublicMapDifficultyResponse difficulty = PublicMapDifficultyResponse.builder().id(difficultyId).build();
        when(mapService.getDifficultyResponsesPublic(List.of(difficultyId)))
                .thenReturn(Map.of(difficultyId, difficulty));
        return ClanWarHit.builder().id(UUID.randomUUID()).war(war).attacker(User.builder().id(1L).build())
                .victim(User.builder().id(2L).build()).mapDifficulty(MapDifficulty.builder().id(difficultyId).build())
                .damage(25.0).guardAfter(broke ? 0.0 : 75.0).broke(broke).build();
    }

    @Test
    void aWarChangeGoesToBothSidesWithTheWholeWar() {
        ClanWarResponse response = warResponse();

        feed.war(war);

        ClanFeedEvent event = published(1).get(0);
        assertThat(event.clanIds()).containsExactly(war.getAttackerClan().getId(), war.getDefenderClan().getId());
        assertThat(event.payload().type()).isEqualTo(ClanFeedType.war);
        assertThat(event.payload().warId()).isEqualTo(war.getId());
        assertThat(event.payload().data()).isSameAs(response);
    }

    @Test
    void anEndedWarAlsoGoesToEveryoneWithTheSameWarBuiltOnce() {
        ClanWarResponse response = warResponse();

        feed.war(war, ClanFeedType.war_ended);

        List<ClanFeedEvent> events = published(2);
        assertThat(events.get(0).payload().type()).isEqualTo(ClanFeedType.war);
        assertThat(events.get(1).clanIds()).isEmpty();
        assertThat(events.get(1).payload().type()).isEqualTo(ClanFeedType.war_ended);
        assertThat(events.get(1).payload().data()).isSameAs(response);
        verify(warResponses, times(1)).of(war);
    }

    @Test
    void aHitCarriesTheMapItLandedOnAndStaysInTheRooms() {
        ClanWarHit hit = hit(false);

        feed.hit(war, hit);

        ClanFeedEvent event = published(1).get(0);
        assertThat(event.payload().type()).isEqualTo(ClanFeedType.hit);
        assertThat(event.payload().data()).isInstanceOfSatisfying(ClanWarHitResponse.class, response -> {
            assertThat(response.id()).isEqualTo(hit.getId());
            assertThat(response.difficulty().getId()).isEqualTo(hit.getMapDifficulty().getId());
        });
    }

    @Test
    void aBreakAlsoGoesToEveryone() {
        feed.hit(war, hit(true));

        List<ClanFeedEvent> events = published(2);
        assertThat(events.get(1).clanIds()).isEmpty();
        assertThat(events.get(1).payload().type()).isEqualTo(ClanFeedType.war_break);
        assertThat(events.get(1).payload().data()).isSameAs(events.get(0).payload().data());
    }

    @Test
    void clanEventsCarryTheClansInTheOrderGiven() {
        Clan first = war.getAttackerClan();
        Clan second = war.getDefenderClan();
        PublicClanResponse firstRef = new PublicClanResponse(first.getId(), null, "A", null, null, null, null, null,
                List.of());
        PublicClanResponse secondRef = new PublicClanResponse(second.getId(), null, "B", null, null, null, null, null,
                List.of());
        when(cosmeticService.publicRefs(List.of(second, first)))
                .thenReturn(Map.of(first.getId(), firstRef, second.getId(), secondRef));

        feed.clans(ClanFeedType.rival_declared, second, first);

        ClanFeedEvent event = published(1).get(0);
        assertThat(event.clanIds()).isEmpty();
        assertThat(event.payload().type()).isEqualTo(ClanFeedType.rival_declared);
        assertThat(event.payload().warId()).isNull();
        assertThat(event.payload().data()).isEqualTo(List.of(secondRef, firstRef));
    }

    @Test
    void aClosedSeasonGoesToEveryoneWithItsTopClans() {
        ClanSeasonResponse season = new ClanSeasonResponse(UUID.randomUUID(), "Season 1", "season-1", null, null,
                null);
        ClanSeasonClosedResponse closed = new ClanSeasonClosedResponse(season, List.of());

        feed.seasonClosed(closed);

        ClanFeedEvent event = published(1).get(0);
        assertThat(event.clanIds()).isEmpty();
        assertThat(event.payload().type()).isEqualTo(ClanFeedType.season_closed);
        assertThat(event.payload().data()).isSameAs(closed);
    }
}
