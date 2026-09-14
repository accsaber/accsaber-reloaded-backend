package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.ClanArenaSpec;
import com.accsaber.backend.model.dto.request.clan.DeclareClanWarRequest;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarPoolEntry;
import com.accsaber.backend.model.entity.clan.war.ClanWarPoolSource;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.clan.war.ClanWarPoolEntryRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.clan.ClanAccessService;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanPermission;
import com.accsaber.backend.service.map.MapService;

@ExtendWith(MockitoExtension.class)
class ClanWarPoolServiceTest {

    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanWarSideRepository sideRepository;
    @Mock
    private ClanWarPoolEntryRepository poolRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private MapDifficultyRepository mapDifficultyRepository;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanCosmeticService cosmeticService;
    @Mock
    private MapService mapService;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanWarPoolService service;

    private final Clan attacker = Clan.builder().id(UUID.randomUUID()).tag("ATK").build();
    private final Clan defender = Clan.builder().id(UUID.randomUUID()).tag("DEF").build();

    @BeforeEach
    void setUp() {
        service = new ClanWarPoolService(warRepository, sideRepository, poolRepository, categoryRepository,
                mapDifficultyRepository, accessService, cosmeticService, mapService, clanProperties);
        lenient().when(mapDifficultyRepository.getReferenceById(any()))
                .thenAnswer(inv -> MapDifficulty.builder().id(inv.getArgument(0)).build());
    }

    private DeclareClanWarRequest request(ClanArena arena) {
        DeclareClanWarRequest request = new DeclareClanWarRequest();
        request.setClanId(defender.getId());
        request.setArena(arena);
        request.setRuleset(ClanRuleset.duel);
        request.setMapDifficultyIds(List.of());
        return request;
    }

    private ClanWar war(ClanArena arena, ClanWarStatus status, ClanArenaSpec spec) {
        return ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender).arena(arena)
                .arenaSpec(spec).status(status).picksDueAt(Instant.now().minusSeconds(1)).build();
    }

    private List<UUID> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    private void allLegal() {
        when(poolRepository.findLegal(anyCollection(), any(), any(), any()))
                .thenAnswer(inv -> List.copyOf(inv.<Collection<UUID>>getArgument(0)));
    }

    @Nested
    class Spec {

        @Test
        void evenStandingsSplitThePoolDownTheMiddle() {
            assertThat(ClanWarPoolService.underdogShare(100, 100, 0.7)).isEqualTo(0.5);
        }

        @Test
        void theUnderdogShareGrowsWithTheGapUpToTheCap() {
            assertThat(ClanWarPoolService.underdogShare(100, 400, 0.7)).isCloseTo(0.65, within(1e-9));
            assertThat(ClanWarPoolService.underdogShare(1, 1_000_000, 0.7)).isCloseTo(0.7, within(1e-5));
        }

        @Test
        void anUnderdogAttackerPicksTheBiggerHalf() {
            ClanArenaSpec spec = service.spec(request(ClanArena.mixed), 100, 1000);

            assertThat(spec.poolSize()).isEqualTo(10);
            assertThat(spec.attackerPicks()).isEqualTo(7);
            assertThat(spec.defenderPicks()).isEqualTo(3);
        }

        @Test
        void aRandomArenaHasNoPicks() {
            ClanArenaSpec spec = service.spec(request(ClanArena.random), 100, 100);

            assertThat(spec.attackerPicks()).isZero();
            assertThat(spec.defenderPicks()).isZero();
        }

        @Test
        void aCategoryTurfWarNeedsItsCategory() {
            assertThatThrownBy(() -> service.spec(request(ClanArena.category_turf), 1, 1))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void aMixedArenaTakesNoRange() {
            DeclareClanWarRequest request = request(ClanArena.mixed);
            request.setComplexityMin(5.0);

            assertThatThrownBy(() -> service.spec(request, 1, 1)).isInstanceOf(ValidationException.class);
        }

        @Test
        void aComplexityTurfWarNeedsARangeThatGoesUp() {
            DeclareClanWarRequest request = request(ClanArena.complexity_turf);
            request.setComplexityMin(9.0);
            request.setComplexityMax(8.0);

            assertThatThrownBy(() -> service.spec(request, 1, 1)).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Seed {

        @Test
        void aRandomArenaFillsTheWholePoolAndGoesStraightToPreparation() {
            ClanWar war = war(ClanArena.random, ClanWarStatus.picking, new ClanArenaSpec(null, null, null, 10, 0, 0));

            service.seed(war, List.of());

            verify(poolRepository).insertRandom(war.getId(), null, "random", 10, null, null, null);
            assertThat(war.getStatus()).isEqualTo(ClanWarStatus.preparing);
            assertThat(war.getStartsAt()).isAfter(Instant.now());
        }

        @Test
        void theAttackerPicksGoInAsPicksAndThePoolStaysOpen() {
            ClanWar war = war(ClanArena.mixed, ClanWarStatus.picking, new ClanArenaSpec(null, null, null, 4, 2, 2));
            allLegal();

            service.seed(war, ids(2));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ClanWarPoolEntry>> saved = ArgumentCaptor.forClass(List.class);
            verify(poolRepository).saveAll(saved.capture());
            assertThat(saved.getValue()).hasSize(2).allSatisfy(entry -> {
                assertThat(entry.getSource()).isEqualTo(ClanWarPoolSource.pick);
                assertThat(entry.getPickedByClan()).isSameAs(attacker);
            });
            assertThat(war.getStatus()).isEqualTo(ClanWarStatus.picking);
        }

        @Test
        void theWrongNumberOfPicksIsRejected() {
            ClanWar war = war(ClanArena.mixed, ClanWarStatus.picking, new ClanArenaSpec(null, null, null, 4, 2, 2));

            assertThatThrownBy(() -> service.seed(war, ids(3))).isInstanceOf(ValidationException.class);
        }

        @Test
        void aPickOutsideTheArenaIsRejected() {
            ClanWar war = war(ClanArena.mixed, ClanWarStatus.picking, new ClanArenaSpec(null, null, null, 4, 2, 2));
            when(poolRepository.findLegal(anyCollection(), any(), any(), any())).thenReturn(List.of(UUID.randomUUID()));

            assertThatThrownBy(() -> service.seed(war, ids(2))).isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class DefensePicks {

        private final User officer = User.builder().id(3L).build();
        private final ClanWarSide side = ClanWarSide.builder().clan(defender).build();

        @BeforeEach
        void officer() {
            lenient().when(accessService.player(3L)).thenReturn(officer);
            lenient().when(sideRepository.findByWar_IdAndClan_Id(any(), eq(defender.getId())))
                    .thenReturn(Optional.of(side));
        }

        @Test
        void aPickTheAttackerAlreadyMadeIsReplacedAndThePoolLocks() {
            ClanWar war = war(ClanArena.mixed, ClanWarStatus.picking, new ClanArenaSpec(null, null, null, 4, 2, 2));
            UUID shared = UUID.randomUUID();
            UUID fresh = UUID.randomUUID();
            when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
            allLegal();
            when(poolRepository.findByWarId(war.getId())).thenReturn(List.of(ClanWarPoolEntry.builder()
                    .mapDifficulty(MapDifficulty.builder().id(shared).build()).pickedByClan(attacker)
                    .source(ClanWarPoolSource.pick).build()));

            service.submitPicks(war.getId(), 3L, List.of(shared, fresh));

            verify(accessService).require(defender.getId(), 3L, ClanPermission.SUBMIT_PICKS);
            verify(poolRepository).insertRandom(war.getId(), defender.getId(), "replacement", 1, null, null, null);
            assertThat(side.getPicksSubmittedAt()).isNotNull();
            assertThat(side.getLeadUser()).isSameAs(officer);
            assertThat(war.getStatus()).isEqualTo(ClanWarStatus.preparing);
        }

        @Test
        void aWarPastPickingTakesNoMorePicks() {
            ClanWar war = war(ClanArena.mixed, ClanWarStatus.preparing, new ClanArenaSpec(null, null, null, 4, 2, 2));
            when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));

            assertThatThrownBy(() -> service.submitPicks(war.getId(), 3L, ids(2)))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void aMissedWindowRollsTheDefenseHalfAtRandom() {
            ClanWar war = war(ClanArena.mixed, ClanWarStatus.picking, new ClanArenaSpec(null, null, null, 4, 1, 3));
            when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));

            service.closePicks(war.getId());

            verify(poolRepository).insertRandom(war.getId(), null, "random", 3, null, null, null);
            assertThat(war.getStatus()).isEqualTo(ClanWarStatus.preparing);
        }

        @Test
        void aWindowStillOpenIsLeftAlone() {
            ClanWar war = war(ClanArena.mixed, ClanWarStatus.picking, new ClanArenaSpec(null, null, null, 4, 1, 3));
            war.setPicksDueAt(Instant.now().plusSeconds(3600));
            when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));

            service.closePicks(war.getId());

            verify(poolRepository, never()).insertRandom(any(), any(), anyString(), anyInt(), any(), any(), any());
        }
    }

    @Test
    void whilePickingEachClanOnlySeesItsOwnPicks() {
        ClanWar war = war(ClanArena.mixed, ClanWarStatus.picking, new ClanArenaSpec(null, null, null, 4, 2, 2));
        UUID attackerPick = UUID.randomUUID();
        when(poolRepository.findByWarId(war.getId())).thenReturn(List.of(ClanWarPoolEntry.builder()
                .mapDifficulty(MapDifficulty.builder().id(attackerPick).build()).pickedByClan(attacker)
                .source(ClanWarPoolSource.pick).build()));
        when(mapService.getDifficultyResponsesPublic(anyCollection())).thenReturn(Map.of());
        when(cosmeticService.publicRefs(anyCollection())).thenAnswer(inv -> inv.<Collection<Clan>>getArgument(0)
                .stream().collect(Collectors.toMap(Clan::getId, clan -> PublicClanResponse.of(clan, List.of()))));

        assertThat(service.pool(war, attacker.getId())).hasSize(1);
        assertThat(service.pool(war, defender.getId())).isEmpty();
        assertThat(service.pool(war, null)).isEmpty();
    }

    @Test
    void thePlaylistWaitsForThePoolToLock() {
        ClanWar war = war(ClanArena.mixed, ClanWarStatus.picking, new ClanArenaSpec(null, null, null, 4, 2, 2));
        when(warRepository.findWithRefsById(war.getId())).thenReturn(Optional.of(war));

        assertThatThrownBy(() -> service.playlistSource(war.getId())).isInstanceOf(ValidationException.class);
        verify(poolRepository, never()).findDifficulties(any());
    }
}
