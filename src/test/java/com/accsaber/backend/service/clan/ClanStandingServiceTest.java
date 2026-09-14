package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.clan.ClanStandingResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanStandingSource;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.clan.ClanSeasonResult;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.clan.ClanSeasonRepository;
import com.accsaber.backend.repository.clan.ClanSeasonResultRepository;
import com.accsaber.backend.repository.clan.ClanSeasonStandingRepository;
import com.accsaber.backend.repository.clan.ClanStandingEventRepository;

@ExtendWith(MockitoExtension.class)
class ClanStandingServiceTest {

    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanSeasonRepository seasonRepository;
    @Mock
    private ClanSeasonStandingRepository standingRepository;
    @Mock
    private ClanSeasonResultRepository resultRepository;
    @Mock
    private ClanStandingEventRepository eventRepository;
    @Mock
    private ClanCosmeticService cosmeticService;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanStandingService service;

    @BeforeEach
    void setUp() {
        clanProperties.setStandingPerSkill(10.0);
        service = new ClanStandingService(clanRepository, seasonRepository, standingRepository, resultRepository,
                eventRepository, cosmeticService, clanProperties);
        lenient().when(cosmeticService.publicRefs(anyCollection())).thenAnswer(inv -> {
            Collection<Clan> clans = inv.getArgument(0);
            return clans.stream().collect(Collectors.toMap(Clan::getId, clan -> PublicClanResponse.of(clan, List.of())));
        });
    }

    private ClanSeasonStandingRepository.RankingRow row(UUID clanId, double base, double earned) {
        return new ClanSeasonStandingRepository.RankingRow() {
            public UUID getClanId() {
                return clanId;
            }

            public double getBaseStanding() {
                return base;
            }

            public double getEarned() {
                return earned;
            }

            public Instant getCreatedAt() {
                return Instant.EPOCH;
            }
        };
    }

    @Test
    void baseStandingIsRosterPlusAlliesTimesTheMultiplier() {
        assertThat(service.baseStanding(Clan.builder().rosterStrength(40.0).allyStrength(5.0).build()))
                .isEqualTo(450.0);
    }

    @Test
    void aLiveRankingNumbersRowsFromThePageOffset() {
        ClanSeason season = ClanSeason.builder().id(UUID.randomUUID()).build();
        UUID clanId = UUID.randomUUID();
        PageRequest secondPage = PageRequest.of(1, 20);
        when(standingRepository.findLiveRanking(season.getId(), 10.0, secondPage))
                .thenReturn(new PageImpl<>(List.of(row(clanId, 300.0, 50.0)), secondPage, 21));
        when(clanRepository.findAllById(List.of(clanId))).thenReturn(List.of(Clan.builder().id(clanId).build()));

        ClanStandingResponse entry = service.ranking(season, secondPage).getContent().get(0);

        assertThat(entry.rank()).isEqualTo(21);
        assertThat(entry.standing()).isEqualTo(350.0);
        assertThat(entry.clan().id()).isEqualTo(clanId);
    }

    @Test
    void aClosedSeasonReadsItsFrozenResults() {
        ClanSeason season = ClanSeason.builder().id(UUID.randomUUID()).closedAt(Instant.now()).build();
        Clan clan = Clan.builder().id(UUID.randomUUID()).build();
        ClanSeasonResult result = ClanSeasonResult.builder().season(season).clan(clan).rank(3)
                .baseStanding(100.0).earned(25.0).build();
        when(resultRepository.findPageBySeasonId(eq(season.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(result)));

        ClanStandingResponse entry = service.ranking(season, PageRequest.of(0, 20)).getContent().get(0);

        assertThat(entry.rank()).isEqualTo(3);
        assertThat(entry.standing()).isEqualTo(125.0);
    }

    @Test
    void askingForTheCurrentSeasonWhenNoneRunsIsNotFound() {
        assertThatThrownBy(() -> service.resolveSeason("current")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aSeasonResolvesBySlug() {
        ClanSeason season = ClanSeason.builder().slug("season-2").build();
        when(seasonRepository.findBySlug("season-2")).thenReturn(Optional.of(season));

        assertThat(service.resolveSeason("season-2")).isSameAs(season);
    }

    @Test
    void aClansLiveStandingAsksTheDatabaseForItsRank() {
        UUID clanId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-01T00:00:00Z");
        Clan clan = Clan.builder().id(clanId).rosterStrength(20.0).createdAt(createdAt).build();
        ClanSeason season = ClanSeason.builder().id(UUID.randomUUID()).build();
        when(clanRepository.findById(clanId)).thenReturn(Optional.of(clan));
        when(seasonRepository.findCurrent(any())).thenReturn(Optional.of(season));
        when(standingRepository.findLiveRank(season.getId(), 10.0, 200.0, createdAt, clanId)).thenReturn(4L);

        ClanStandingResponse standing = service.standingOf(clanId, null);

        assertThat(standing.rank()).isEqualTo(4);
        assertThat(standing.baseStanding()).isEqualTo(200.0);
        assertThat(standing.earned()).isZero();
    }

    @Test
    void aDebitClampsToWhatTheClanHasEarnedAndIsWrittenAsApplied() {
        UUID seasonId = UUID.randomUUID();
        UUID clanId = UUID.randomUUID();
        when(standingRepository.lockEarned(seasonId, clanId)).thenReturn(30.0);
        when(eventRepository.insertIfAbsent(seasonId, clanId, -30.0, "war_break", "hit-1")).thenReturn(1);

        service.apply(seasonId, clanId, -50.0, ClanStandingSource.war_break, "hit-1");

        verify(standingRepository).ensureRow(seasonId, clanId);
        verify(standingRepository).addEarned(seasonId, clanId, -30.0);
    }

    @Test
    void aSourceAlreadyBankedChangesNothing() {
        UUID seasonId = UUID.randomUUID();
        UUID clanId = UUID.randomUUID();
        when(eventRepository.insertIfAbsent(seasonId, clanId, 40.0, "mission", "m-1")).thenReturn(0);

        service.apply(seasonId, clanId, 40.0, ClanStandingSource.mission, "m-1");

        verify(standingRepository, never()).addEarned(any(), any(), anyDouble());
    }
}
