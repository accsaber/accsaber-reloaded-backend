package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.clan.ClanSeasonRequest;
import com.accsaber.backend.model.dto.request.clan.ClanSeasonRewardRequest;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.clan.ClanSeasonResult;
import com.accsaber.backend.model.entity.clan.ClanSeasonReward;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.repository.clan.ClanItemRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.clan.ClanSeasonRepository;
import com.accsaber.backend.repository.clan.ClanSeasonResultRepository;
import com.accsaber.backend.repository.clan.ClanSeasonRewardRepository;
import com.accsaber.backend.repository.clan.ClanSeasonStandingRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.service.clan.war.ClanWarService;
import com.accsaber.backend.service.item.ItemService;

@ExtendWith(MockitoExtension.class)
class ClanSeasonServiceTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ClanSeasonRepository seasonRepository;
    @Mock
    private ClanSeasonResultRepository resultRepository;
    @Mock
    private ClanSeasonRewardRepository rewardRepository;
    @Mock
    private ClanRepository clanRepository;
    @Mock
    private ClanItemRepository clanItemRepository;
    @Mock
    private ClanStandingService standingService;
    @Mock
    private ClanWarService warService;
    @Mock
    private ItemService itemService;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanSeasonService service;

    @BeforeEach
    void setUp() {
        clanProperties.setSeasonLength(Period.ofMonths(6));
        clanProperties.setMissionContribution(100.0);
        service = new ClanSeasonService(seasonRepository, resultRepository, rewardRepository, clanRepository,
                clanItemRepository, standingService, warService, itemService, itemRepository, clanProperties);
        lenient().when(clanRepository.getReferenceById(any()))
                .thenAnswer(inv -> Clan.builder().id(inv.getArgument(0)).build());
    }

    @Nested
    class EnsureCurrent {

        @Test
        void aRunningOrUpcomingSeasonMeansNothingOpens() {
            when(seasonRepository.existsOpenUntilAfter(any())).thenReturn(true);

            service.ensureCurrent();

            verify(seasonRepository, never()).save(any());
        }

        @Test
        void theFirstSeasonOpensNowAndRunsSixMonths() {
            ArgumentCaptor<ClanSeason> saved = ArgumentCaptor.forClass(ClanSeason.class);

            service.ensureCurrent();

            verify(seasonRepository).save(saved.capture());
            assertThat(saved.getValue().getSlug()).isEqualTo("season-1");
            assertThat(saved.getValue().getStartsAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
            assertThat(ChronoUnit.DAYS.between(saved.getValue().getStartsAt(), saved.getValue().getEndsAt()))
                    .isBetween(180L, 185L);
        }

        @Test
        void theNextSeasonIsNumberedAfterTheOnesBeforeIt() {
            when(seasonRepository.count()).thenReturn(3L);
            when(seasonRepository.findTopByOrderByEndsAtDesc()).thenReturn(Optional.of(
                    ClanSeason.builder().endsAt(Instant.now().minus(1, ChronoUnit.HOURS)).build()));
            ArgumentCaptor<ClanSeason> saved = ArgumentCaptor.forClass(ClanSeason.class);

            service.ensureCurrent();

            verify(seasonRepository).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("Season 4");
        }
    }

    @Nested
    class Close {

        private final UUID seasonId = UUID.randomUUID();
        private final ClanSeason season = ClanSeason.builder().id(seasonId).name("Season 1").build();
        private final UUID first = UUID.randomUUID();
        private final UUID second = UUID.randomUUID();

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

        private ClanSeasonRepository.ContributorView contributor(Long userId) {
            return new ClanSeasonRepository.ContributorView() {
                public Long getUserId() {
                    return userId;
                }

                public double getContribution() {
                    return 1.0;
                }
            };
        }

        private ClanSeasonReward reward(int from, int to, ItemType type) {
            Item item = Item.builder().id(UUID.randomUUID()).type(type).build();
            return ClanSeasonReward.builder().rankFrom(from).rankTo(to).item(item).quantity(1).build();
        }

        @BeforeEach
        void lockSeason() {
            when(seasonRepository.findByIdForUpdate(seasonId)).thenReturn(Optional.of(season));
        }

        @Test
        void anAlreadyClosedSeasonIsLeftAlone() {
            season.setClosedAt(Instant.now());

            service.close(seasonId);

            verify(resultRepository, never()).save(any());
        }

        @Test
        void closingFreezesTheRankingPaysBandsInRankOrderAndStampsTheSeason() {
            when(standingService.fullLiveRanking(seasonId)).thenReturn(List.of(row(first, 500.0, 120.0),
                    row(second, 300.0, 0.0)));
            ItemType clanBanner = ItemType.builder().key("clan_banner")
                    .parentType(ItemType.builder().key("clan_cosmetic").build()).build();
            ItemType crate = ItemType.builder().key("crate").build();
            ClanSeasonReward banner = reward(1, 1, clanBanner);
            ClanSeasonReward crates = reward(1, 2, crate);
            when(rewardRepository.findBySeasonId(seasonId)).thenReturn(List.of(banner, crates));
            when(seasonRepository.findContributors(seasonId, first, 100.0))
                    .thenReturn(List.of(contributor(11L), contributor(12L)));
            when(seasonRepository.findContributors(seasonId, second, 100.0))
                    .thenReturn(List.of(contributor(21L)));

            service.close(seasonId);

            verify(warService).endSeason(seasonId);
            ArgumentCaptor<ClanSeasonResult> results = ArgumentCaptor.forClass(ClanSeasonResult.class);
            verify(resultRepository, times(2)).save(results.capture());
            assertThat(results.getAllValues()).extracting(ClanSeasonResult::getRank).containsExactly(1, 2);
            assertThat(results.getAllValues().get(0).getEarned()).isEqualTo(120.0);
            verify(clanItemRepository).grantItem(first, banner.getItem().getId(), "season", seasonId.toString());
            InOrder order = inOrder(itemService);
            order.verify(itemService).awardSystem(eq(11L), eq(crates.getItem().getId()), eq(ItemSource.clan_season),
                    anyString(), anyString(), eq(1));
            order.verify(itemService).awardSystem(eq(12L), eq(crates.getItem().getId()), eq(ItemSource.clan_season),
                    anyString(), anyString(), eq(1));
            order.verify(itemService).awardSystem(eq(21L), eq(crates.getItem().getId()), eq(ItemSource.clan_season),
                    anyString(), anyString(), eq(1));
            assertThat(season.getClosedAt()).isNotNull();
        }

        @Test
        void clansBelowEveryBandAreNotLookedAt() {
            when(standingService.fullLiveRanking(seasonId)).thenReturn(List.of(row(first, 1.0, 0.0),
                    row(second, 0.5, 0.0)));
            when(rewardRepository.findBySeasonId(seasonId))
                    .thenReturn(List.of(reward(1, 1, ItemType.builder().key("crate").build())));

            service.close(seasonId);

            verify(seasonRepository, never()).findContributors(eq(seasonId), eq(second), anyDouble());
        }
    }

    @Nested
    class Admin {

        private final Instant start = Instant.parse("2027-01-01T00:00:00Z");

        private ClanSeasonRequest request(String name, Instant startsAt, Instant endsAt) {
            ClanSeasonRequest request = new ClanSeasonRequest();
            request.setName(name);
            request.setStartsAt(startsAt);
            request.setEndsAt(endsAt);
            return request;
        }

        @Test
        void aNewSeasonTakesItsSlugFromItsName() {
            when(seasonRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            var response = service.create(request("Winter Clash", start, start.plus(90, ChronoUnit.DAYS)));

            assertThat(response.slug()).isEqualTo("winter-clash");
        }

        @Test
        void aSeasonHasToEndAfterItStarts() {
            assertThatThrownBy(() -> service.create(request("Backwards", start, start)))
                    .isInstanceOf(ValidationException.class);
            verify(seasonRepository, never()).saveAndFlush(any());
        }

        @Test
        void anOverlappingSeasonIsAConflict() {
            when(seasonRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("overlap"));

            assertThatThrownBy(() -> service.create(request("Overlap", start, start.plus(1, ChronoUnit.DAYS))))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void aClosedSeasonCannotChange() {
            ClanSeason closed = ClanSeason.builder().id(UUID.randomUUID()).closedAt(Instant.now()).build();
            when(seasonRepository.findById(closed.getId())).thenReturn(Optional.of(closed));

            assertThatThrownBy(() -> service.update(closed.getId(), request("Renamed", null, null)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void aRewardBandCannotRunBackwards() {
            ClanSeason open = ClanSeason.builder().id(UUID.randomUUID()).build();
            when(seasonRepository.findById(open.getId())).thenReturn(Optional.of(open));
            ClanSeasonRewardRequest reward = new ClanSeasonRewardRequest();
            reward.setRankFrom(5);
            reward.setRankTo(1);
            reward.setItemId(UUID.randomUUID());

            assertThatThrownBy(() -> service.addReward(open.getId(), reward)).isInstanceOf(ValidationException.class);
            verify(rewardRepository, never()).save(any());
        }
    }
}
