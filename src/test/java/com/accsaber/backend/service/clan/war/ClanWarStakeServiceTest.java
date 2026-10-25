package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.clan.ClanStandingSource;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.service.clan.ClanStandingService;
import com.accsaber.backend.service.clan.ClanStrengthService;
import com.accsaber.backend.service.clan.ClanStrengthService.MemberStrength;

@ExtendWith(MockitoExtension.class)
class ClanWarStakeServiceTest {

    @Mock
    private ClanWarSideRepository sideRepository;
    @Mock
    private ClanWarParticipantRepository participantRepository;
    @Mock
    private ClanWarHitRepository hitRepository;
    @Mock
    private ClanStrengthService strengthService;
    @Mock
    private ClanStandingService standingService;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanWarStakeService service;

    private final Clan stars = Clan.builder().id(UUID.randomUUID()).build();
    private final Clan genx = Clan.builder().id(UUID.randomUUID()).build();
    private final Clan fish = Clan.builder().id(UUID.randomUUID()).build();
    private final ClanSeason season = ClanSeason.builder().id(UUID.randomUUID()).build();
    private ClanWar war;

    @BeforeEach
    void setUp() {
        service = new ClanWarStakeService(sideRepository, participantRepository, hitRepository, strengthService, standingService,
                clanProperties);
        war = ClanWar.builder().id(UUID.randomUUID()).season(season).attackerClan(stars).defenderClan(genx)
                .startsAt(Instant.parse("2026-11-01T00:00:00Z")).build();
        lenient().when(standingService.currentStanding(stars)).thenReturn(7000.0);
        lenient().when(standingService.currentStanding(genx)).thenReturn(7000.0);
        lenient().when(standingService.currentStanding(fish)).thenReturn(5000.0);
    }

    private ClanWarSide side(Clan clan, ClanWarTeam team, double stake, double remaining) {
        return ClanWarSide.builder().war(war).clan(clan).team(team).stake(stake).stakeRemaining(remaining).build();
    }

    private void weights(MemberStrength... members) {
        when(strengthService.memberStrengths(anyCollection())).thenAnswer(inv -> {
            List<UUID> clans = new ArrayList<>(inv.getArgument(0));
            return List.of(members).stream().filter(m -> clans.contains(m.clanId())).toList();
        });
    }

    @Nested
    class Rebalance {

        @Test
        void twoLeadsEachPutUpTheSmallerStanding() {
            ClanWarSide attack = side(stars, ClanWarTeam.attack, 0, 0);
            ClanWarSide defend = side(genx, ClanWarTeam.defend, 0, 0);
            when(standingService.currentStanding(genx)).thenReturn(4000.0);
            weights(new MemberStrength(stars.getId(), 1L, 90, 1.0), new MemberStrength(genx.getId(), 2L, 60, 1.0));

            service.rebalance(List.of(attack, defend));

            assertThat(attack.getStake()).isEqualTo(4000.0);
            assertThat(attack.getStakeRemaining()).isEqualTo(4000.0);
            assertThat(defend.getStake()).isEqualTo(4000.0);
        }

        @Test
        void aCoalitionSplitsItsStakeByRosterWeightAndBothTeamsRiskTheSameTotal() {
            when(standingService.currentStanding(stars)).thenReturn(10000.0);
            ClanWarSide attack = side(stars, ClanWarTeam.attack, 0, 0);
            ClanWarSide defend = side(genx, ClanWarTeam.defend, 0, 0);
            ClanWarSide ally = side(fish, ClanWarTeam.defend, 0, 0);
            weights(new MemberStrength(stars.getId(), 1L, 95, 1.0),
                    new MemberStrength(genx.getId(), 2L, 80, 0.75), new MemberStrength(fish.getId(), 3L, 70, 0.25));

            service.rebalance(List.of(attack, defend, ally));

            double exposure = 8500.0 * (0.75 + 0.25 * 0.3);
            assertThat(attack.getStake()).isCloseTo(exposure, within(1e-9));
            assertThat(defend.getStake() + ally.getStake()).isCloseTo(exposure, within(1e-9));
            assertThat(defend.getStake()).isCloseTo(8500.0 * 0.75, within(1e-9));
            assertThat(ally.getStake()).isCloseTo(8500.0 * 0.25 * 0.3, within(1e-9));
        }

        @Test
        void slicesOnlyGrowAndKeepWhatWasAlreadyDrained() {
            ClanWarSide attack = side(stars, ClanWarTeam.attack, 7000, 6000);
            ClanWarSide defend = side(genx, ClanWarTeam.defend, 3000, 1000);
            ClanWarSide ally = side(fish, ClanWarTeam.defend, 0, 0);
            weights(new MemberStrength(stars.getId(), 1L, 95, 1.0),
                    new MemberStrength(genx.getId(), 2L, 80, 0.5), new MemberStrength(fish.getId(), 3L, 70, 0.5));

            service.rebalance(List.of(attack, defend, ally));

            assertThat(attack.getStake()).isEqualTo(7000.0);
            assertThat(attack.getStakeRemaining()).isEqualTo(6000.0);
            assertThat(defend.getStake()).isEqualTo(3500.0);
            assertThat(defend.getStakeRemaining()).isEqualTo(1500.0);
            assertThat(ally.getStake()).isCloseTo(3500.0 * 0.3, within(1e-9));
        }

        @Test
        void aFallenOrDepartedClanNeverGrowsBack() {
            ClanWarSide attack = side(stars, ClanWarTeam.attack, 7000, 7000);
            ClanWarSide fallen = side(genx, ClanWarTeam.defend, 3000, 0);
            ClanWarSide departed = side(fish, ClanWarTeam.defend, 2000, 0);
            departed.setLeftAt(Instant.now());
            weights(new MemberStrength(stars.getId(), 1L, 95, 1.0));

            service.rebalance(List.of(attack, fallen, departed));

            assertThat(fallen.getStake()).isEqualTo(3000.0);
            assertThat(fallen.getStakeRemaining()).isZero();
            assertThat(departed.getStake()).isEqualTo(2000.0);
        }
    }

    @Nested
    class Ledger {

        @Test
        void theLeadsRiskInFullAndAnAllyRisksItsShare() {
            assertThat(service.risk(side(genx, ClanWarTeam.defend, 0, 0))).isEqualTo(1.0);
            assertThat(service.risk(side(fish, ClanWarTeam.defend, 0, 0))).isEqualTo(0.3);
        }

        @Test
        void aBreakNeverTakesMoreThanTheSliceHasLeft() {
            ClanWarSide side = side(genx, ClanWarTeam.defend, 1000, 30);
            ClanWarParticipant victim = ClanWarParticipant.builder().standingWeight(1.0).build();

            assertThat(service.breakAmount(List.of(side), side, victim)).isEqualTo(30.0);
        }

        @Test
        void aBountyCarriesHalfOfWhatItWasIntoTheNextLifeAndClimbsBackWithDamageDealt() {
            assertThat(service.bounty(List.of())).isEqualTo(1.0);
            assertThat(service.bounty(List.of(0.0))).isEqualTo(1.0);
            assertThat(service.bounty(List.of(0.0, 0.0))).isCloseTo(0.25 + 0.75 * 0.5, within(1e-9));
            assertThat(service.bounty(List.of(100_000.0, 0.0))).isCloseTo(0.25 + 1.5 * 0.5, within(1e-9));
            assertThat(service.bounty(List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))).isCloseTo(0.25, within(0.01));
            assertThat(service.bounty(List.of(0.0, 150.0))).isCloseTo(1.75 - (1.75 - 0.625) * Math.exp(-1), within(1e-9));
            ClanWarSide side = side(genx, ClanWarTeam.defend, 1000, 1000);
            ClanWarParticipant victim = ClanWarParticipant.builder().standingWeight(0.1).bounty(0.25).build();

            assertThat(service.breakAmount(List.of(side), side, victim)).isCloseTo(1000 * 0.4 * 0.1 * 0.25, within(1e-9));
        }

        @Test
        void aTeamLosesOnceNoneOfItsClansHaveAnythingLeft() {
            ClanWarSide fallen = side(genx, ClanWarTeam.defend, 3000, 0);
            ClanWarSide departed = side(fish, ClanWarTeam.defend, 2000, 0);
            departed.setLeftAt(Instant.now());
            ClanWarSide standing = side(stars, ClanWarTeam.attack, 7000, 100);

            assertThat(service.lost(List.of(fallen, departed, standing), ClanWarTeam.defend)).isTrue();
            assertThat(service.lost(List.of(fallen, departed, standing), ClanWarTeam.attack)).isFalse();
        }

        @Test
        void aDepartedSliceStaysOnTheBooksAsDrained() {
            ClanWarSide lead = side(genx, ClanWarTeam.defend, 3000, 3000);
            ClanWarSide departed = side(fish, ClanWarTeam.defend, 1000, 0);
            departed.setLeftAt(Instant.now());

            assertThat(service.drainedShare(List.of(lead, departed), ClanWarTeam.defend)).isEqualTo(0.25);
            assertThat(service.fullTeamStake(List.of(lead, departed), ClanWarTeam.defend))
                    .isCloseTo(3000.0 + 1000.0 / 0.3, within(1e-9));
        }

        @Test
        void engagementIsTheRecentlyActiveShareCountedInFullFromHalf() {
            when(participantRepository.findRecentlyActiveShare(war.getId(), "defend",
                    war.getStartsAt().minus(Duration.ofDays(14)))).thenReturn(0.2, 0.8);

            assertThat(service.engagement(war, ClanWarTeam.defend)).isCloseTo(0.4, within(1e-9));
            assertThat(service.engagement(war, ClanWarTeam.defend)).isEqualTo(1.0);
        }
    }

    @Nested
    class Forfeit {

        @Test
        void aLeavingClanHandsWhatItHasLeftToTheOtherTeamBySlice() {
            ClanWarSide stars = side(ClanWarStakeServiceTest.this.stars, ClanWarTeam.attack, 6000, 6000);
            ClanWarSide genx = side(ClanWarStakeServiceTest.this.genx, ClanWarTeam.attack, 2000, 2000);
            ClanWarSide leaving = side(fish, ClanWarTeam.defend, 2000, 800);
            List<ClanWarSide> sides = List.of(stars, genx, leaving);

            service.forfeit(war, sides, leaving);

            assertThat(leaving.getStakeRemaining()).isZero();
            assertThat(leaving.getLeftAt()).isNotNull();
            String source = "forfeit:" + war.getId() + ":" + fish.getId();
            verify(standingService).applyAll(season.getId(), Map.of(fish.getId(), -800.0, stars.getClan().getId(),
                    600.0, genx.getClan().getId(), 200.0), ClanStandingSource.war_break, source);
            verify(sideRepository).saveAll(sides);
        }

        @Test
        void aWinPaysBonusStandingSplitBySliceAndScaledByTheLosersEngagement() {
            ClanWarSide stars = side(ClanWarStakeServiceTest.this.stars, ClanWarTeam.attack, 6000, 1000);
            ClanWarSide genx = side(ClanWarStakeServiceTest.this.genx, ClanWarTeam.attack, 2000, 0);

            service.awardWin(war, List.of(stars, genx), 0.5);

            verify(standingService).applyAll(season.getId(), Map.of(stars.getClan().getId(), 375.0,
                    genx.getClan().getId(), 125.0), ClanStandingSource.war_win, war.getId().toString());
        }

        @Test
        void aClanWithNothingLeftHandsNothingOver() {
            ClanWarSide leaving = side(fish, ClanWarTeam.defend, 2000, 0);

            service.forfeit(war, List.of(side(stars, ClanWarTeam.attack, 6000, 6000), leaving), leaving);

            verifyNoInteractions(standingService);
        }
    }
}
