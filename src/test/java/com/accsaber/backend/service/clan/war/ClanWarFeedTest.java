package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.accsaber.backend.model.dto.response.clan.ClanWarHitResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarHit;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanFeedEvent;
import com.accsaber.backend.service.map.MapService;

@ExtendWith(MockitoExtension.class)
class ClanWarFeedTest {

    @Mock
    private ClanWarResponses warResponses;
    @Mock
    private MapService mapService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ClanWarFeed feed;

    private final ClanWar war = ClanWar.builder().id(UUID.randomUUID())
            .attackerClan(Clan.builder().id(new UUID(1L, 1L)).build())
            .defenderClan(Clan.builder().id(new UUID(2L, 1L)).build()).build();

    private ClanFeedEvent published() {
        ArgumentCaptor<ClanFeedEvent> event = ArgumentCaptor.forClass(ClanFeedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        return event.getValue();
    }

    @Test
    void aWarChangeGoesToBothSidesWithTheWholeWar() {
        ClanWarResponse response = new ClanWarResponse(war.getId(), null, null, null, null, null, null, null, null,
                null, null, null, null);
        when(warResponses.of(war)).thenReturn(response);

        feed.war(war);

        ClanFeedEvent event = published();
        assertThat(event.clanIds()).containsExactly(war.getAttackerClan().getId(), war.getDefenderClan().getId());
        assertThat(event.payload().type()).isEqualTo("war");
        assertThat(event.payload().warId()).isEqualTo(war.getId());
        assertThat(event.payload().data()).isSameAs(response);
    }

    @Test
    void aHitCarriesTheMapItLandedOn() {
        UUID difficultyId = UUID.randomUUID();
        PublicMapDifficultyResponse difficulty = PublicMapDifficultyResponse.builder().id(difficultyId).build();
        when(mapService.getDifficultyResponsesPublic(List.of(difficultyId)))
                .thenReturn(Map.of(difficultyId, difficulty));
        ClanWarHit hit = ClanWarHit.builder().id(UUID.randomUUID()).war(war).attacker(User.builder().id(1L).build())
                .victim(User.builder().id(2L).build()).mapDifficulty(MapDifficulty.builder().id(difficultyId).build())
                .damage(25.0).guardAfter(75.0).build();

        feed.hit(war, hit);

        ClanFeedEvent event = published();
        assertThat(event.payload().type()).isEqualTo("hit");
        assertThat(event.payload().data()).isInstanceOfSatisfying(ClanWarHitResponse.class, response -> {
            assertThat(response.id()).isEqualTo(hit.getId());
            assertThat(response.difficulty()).isSameAs(difficulty);
        });
    }
}
