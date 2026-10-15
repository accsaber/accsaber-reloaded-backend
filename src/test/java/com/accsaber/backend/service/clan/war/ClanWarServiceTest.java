package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.ClanArenaSpec;
import com.accsaber.backend.model.dto.request.clan.DeclareClanWarRequest;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.clan.ClanWarModeAxis;
import com.accsaber.backend.model.entity.clan.war.ClanArena;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanWarEndedEvent;
import com.accsaber.backend.repository.clan.ClanAllianceRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarAidRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.service.clan.ChatNotice;
import com.accsaber.backend.service.clan.ClanAccessService;
import com.accsaber.backend.service.clan.ClanChatChannel;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanFeed;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanNotifier;
import com.accsaber.backend.service.clan.ClanPermission;
import com.accsaber.backend.service.clan.ClanRoster;
import com.accsaber.backend.service.clan.ClanStandingService;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.model.dto.response.clan.ClanFeedType;

@ExtendWith(MockitoExtension.class)
class ClanWarServiceTest {

    @Mock
    private ClanFeed feed;
    @Mock
    private ClanNotifier notifier;
    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanWarSideRepository sideRepository;
    @Mock
    private ClanWarParticipantRepository participantRepository;
    @Mock
    private ClanAllianceRepository allianceRepository;
    @Mock
    private ClanMemberRepository memberRepository;
    @Mock
    private ClanRoster roster;
    @Mock
    private ClanAccessService accessService;
    @Mock
    private ClanLevelService levelService;
    @Mock
    private ClanStandingService standingService;
    @Mock
    private ClanCosmeticService cosmeticService;
    @Mock
    private ClanWarPoolService poolService;
    @Mock
    private ClanChatChannel chatChannel;
    @Mock
    private ClanWarScoreGate scoreGate;
    @Mock
    private ClanWarHitRepository hitRepository;
    @Mock
    private MapService mapService;
    @Mock
    private ClanWarAidRepository aidRepository;
    @Mock
    private ClanWarStakeService stakeService;
    @Mock
    private ClanWarRosterService rosterService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanWarService service;

    private final Clan attacker = Clan.builder().id(new UUID(1L, 1L)).tag("ATK").build();
    private final Clan defender = Clan.builder().id(new UUID(2L, 1L)).tag("DEF").build();
    private final User commander = User.builder().id(1L).name("Commander").build();
    private final ClanArenaSpec spec = new ClanArenaSpec(null, null, null, 10, 5, 5);

    @BeforeEach
    void setUp() {
        lenient().when(warRepository.lockOrThrow(any())).thenCallRealMethod();
        service = new ClanWarService(warRepository, sideRepository, participantRepository, hitRepository, mapService,
                allianceRepository, memberRepository, roster, accessService, levelService, standingService,
                cosmeticService, poolService, new ClanWarResponses(sideRepository, cosmeticService), feed, notifier, chatChannel,
                scoreGate, rosterService, aidRepository, stakeService, eventPublisher, clanProperties);
        lenient().when(accessService.player(1L)).thenReturn(commander);
        lenient().when(cosmeticService.publicRefs(anyCollection())).thenAnswer(inv -> inv.<Collection<Clan>>getArgument(0)
                .stream().collect(Collectors.toMap(Clan::getId, clan -> PublicClanResponse.of(clan, List.of()),
                        (first, second) -> first)));
        lenient().when(warRepository.saveAndFlush(any())).thenAnswer(inv -> {
            ClanWar war = inv.getArgument(0);
            if (war.getId() == null) {
                war.setId(UUID.randomUUID());
            }
            return war;
        });
    }

    @Nested
    class Declare {

        private DeclareClanWarRequest request;

        @BeforeEach
        void readyToDeclare() {
            request = new DeclareClanWarRequest();
            request.setClanId(defender.getId());
            request.setArena(ClanArena.mixed);
            request.setRuleset(ClanRuleset.duel);
            request.setMapDifficultyIds(List.of());
            lenient().when(standingService.currentSeason())
                    .thenReturn(Optional.of(ClanSeason.builder().id(UUID.randomUUID()).build()));
            lenient().when(roster.lockPair(attacker.getId(), defender.getId())).thenReturn(List.of(attacker, defender));
            lenient().when(levelService.hasWarMode(eq(attacker), any(), any())).thenReturn(true);
            lenient().when(standingService.currentStanding(attacker)).thenReturn(1000.0);
            lenient().when(standingService.currentStanding(defender)).thenReturn(800.0);
            lenient().when(poolService.spec(eq(request), anyDouble(), anyDouble())).thenReturn(spec);
        }

        @Test
        void aCommanderDeclaresAndTheTwoLeadsGetTheirStakesWorkedOut() {
            when(sideRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            service.declare(attacker.getId(), 1L, request);

            verify(accessService).require(attacker.getId(), 1L, ClanPermission.DECLARE_WAR);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ClanWarSide>> sides = ArgumentCaptor.forClass(List.class);
            verify(sideRepository).saveAllAndFlush(sides.capture());
            assertThat(sides.getValue()).extracting(ClanWarSide::getClan, ClanWarSide::getTeam)
                    .containsExactly(tuple(attacker, ClanWarTeam.attack), tuple(defender, ClanWarTeam.defend));
            assertThat(sides.getValue()).extracting(ClanWarSide::getStandingAtDeclare).containsExactly(1000.0, 800.0);
            assertThat(sides.getValue().get(0).getLeadUser()).isSameAs(commander);
            assertThat(sides.getValue().get(1).getLeadUser()).isNull();
            verify(stakeService).rebalance(sides.getValue());
            ArgumentCaptor<ClanWar> war = ArgumentCaptor.forClass(ClanWar.class);
            verify(warRepository).saveAndFlush(war.capture());
            assertThat(war.getValue().getPicksDueAt()).isNotNull();
            verify(poolService).seed(war.getValue(), List.of());
            verify(chatChannel).announce(attacker, ChatNotice.ofWar(ChatEvent.war_declared, commander, defender,
                    war.getValue()));
            verify(chatChannel).announce(defender, ChatNotice.ofWar(ChatEvent.war_received, commander, attacker,
                    war.getValue()));
            verify(feed).war(war.getValue(), ClanFeedType.war_declared);
            verify(notifier).warDeclared(war.getValue());
        }

        @Test
        void aClanAlreadyAttackingCannotOpenASecondWar() {
            when(warRepository.existsOpenAttack(attacker.getId(), null)).thenReturn(true);

            assertThatThrownBy(() -> service.declare(attacker.getId(), 1L, request))
                    .isInstanceOf(ConflictException.class);
            verify(warRepository, never()).saveAndFlush(any());
        }

        @Test
        void theSameClanCannotBeAttackedAgainRightAfterAWar() {
            when(warRepository.findLastAttackEndedAt(attacker.getId(), defender.getId()))
                    .thenReturn(Optional.of(Instant.now().minus(Duration.ofDays(2))));

            assertThatThrownBy(() -> service.declare(attacker.getId(), 1L, request))
                    .isInstanceOf(ConflictException.class);
            verify(warRepository, never()).saveAndFlush(any());
        }

        @Test
        void aPacifistClanCannotDeclare() {
            attacker.setPacifistFrom(Instant.now().minusSeconds(60));

            assertThatThrownBy(() -> service.declare(attacker.getId(), 1L, request))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("pacifist");
            attacker.setPacifistFrom(null);
        }

        @Test
        void aPacifistClanCannotBeAttacked() {
            defender.setPacifistFrom(Instant.now().minusSeconds(60));

            assertThatThrownBy(() -> service.declare(attacker.getId(), 1L, request))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("pacifist");
            defender.setPacifistFrom(null);
        }

        @Test
        void aClanStillWaitingOutItsPacifistDelayCanBeAttacked() {
            when(sideRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            defender.setPacifistFrom(Instant.now().plus(Duration.ofDays(3)));

            service.declare(attacker.getId(), 1L, request);

            verify(poolService).seed(any(), any());
            defender.setPacifistFrom(null);
        }

        @Test
        void anAllyIsOffLimits() {
            when(allianceRepository.existsActiveBetween(attacker.getId(), defender.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.declare(attacker.getId(), 1L, request))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void aModeTheClanHasNotUnlockedIsRefused() {
            when(levelService.hasWarMode(attacker, ClanWarModeAxis.ruleset, "duel")).thenReturn(false);

            assertThatThrownBy(() -> service.declare(attacker.getId(), 1L, request))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void punchingTooFarDownIsRefused() {
            when(standingService.currentStanding(defender)).thenReturn(400.0);

            assertThatThrownBy(() -> service.declare(attacker.getId(), 1L, request))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void aRetaliationIgnoresTheFloor() {
            when(sideRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(standingService.currentStanding(defender)).thenReturn(400.0);
            lenient().when(warRepository.existsOpenAttack(defender.getId(), attacker.getId())).thenReturn(true);

            service.declare(attacker.getId(), 1L, request);

            verify(poolService).seed(any(), any());
        }

        @Test
        void aClanWithNoStandingHasNothingToStake() {
            when(standingService.currentStanding(defender)).thenReturn(0.0);

            assertThatThrownBy(() -> service.declare(attacker.getId(), 1L, request))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void noSeasonMeansNoWars() {
            when(standingService.currentSeason()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.declare(attacker.getId(), 1L, request))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void aClanCannotDeclareOnItself() {
            request.setClanId(attacker.getId());

            assertThatThrownBy(() -> service.declare(attacker.getId(), 1L, request))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Retreat {

        private ClanWar war;
        private final ClanWarSide side = ClanWarSide.builder().clan(attacker).build();

        @BeforeEach
        void openWar() {
            war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender)
                    .arena(ClanArena.mixed).arenaSpec(spec).status(ClanWarStatus.active).build();
            when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
            lenient().when(sideRepository.findByWar_IdAndClan_Id(war.getId(), attacker.getId()))
                    .thenReturn(Optional.of(side));
            lenient().when(sideRepository.findByWarId(war.getId())).thenReturn(List.of(
                    ClanWarSide.builder().war(war).clan(attacker).team(ClanWarTeam.attack).build(),
                    ClanWarSide.builder().war(war).clan(defender).team(ClanWarTeam.defend).build()));
        }

        private void rank(ClanRole role) {
            when(accessService.require(attacker.getId(), 1L, ClanPermission.DECLARE_WAR))
                    .thenReturn(ClanMember.builder().user(commander).role(role).build());
        }

        @Test
        void theLeadPullsOutAndBothChatsHearIt() {
            side.setLeadUser(commander);
            rank(ClanRole.commander);

            var response = service.retreat(war.getId(), 1L);

            assertThat(response.status()).isEqualTo(ClanWarStatus.ended);
            assertThat(war.getOutcome()).isEqualTo(ClanWarOutcome.retreated);
            assertThat(war.getEndedAt()).isNotNull();
            verify(chatChannel).announce(attacker, ChatNotice.ofWar(ChatEvent.war_ended, commander, defender, war));
            verify(chatChannel).announce(defender, ChatNotice.ofWar(ChatEvent.war_ended, commander, attacker, war));
        }

        @Test
        void anotherCommanderCannotOverruleTheLead() {
            side.setLeadUser(User.builder().id(9L).build());
            rank(ClanRole.commander);

            assertThatThrownBy(() -> service.retreat(war.getId(), 1L)).isInstanceOf(ForbiddenException.class);
        }

        @Test
        void theFounderCanAlwaysCallIt() {
            side.setLeadUser(User.builder().id(9L).build());
            rank(ClanRole.founder);

            service.retreat(war.getId(), 1L);

            assertThat(war.getOutcome()).isEqualTo(ClanWarOutcome.retreated);
        }

        @Test
        void aFinishedWarCannotBeRetreatedFrom() {
            side.setLeadUser(commander);
            rank(ClanRole.commander);
            war.setStatus(ClanWarStatus.ended);

            assertThatThrownBy(() -> service.retreat(war.getId(), 1L)).isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void endingAnAlreadyEndedWarDoesNothing() {
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender)
                .status(ClanWarStatus.ended).outcome(ClanWarOutcome.drawn).build();
        when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));

        service.end(war.getId(), ClanWarOutcome.season_ended);

        assertThat(war.getOutcome()).isEqualTo(ClanWarOutcome.drawn);
        verify(chatChannel, never()).announce(any(), any());
        verify(feed, never()).war(any(), any());
        verify(notifier, never()).warEnded(any(), any());
    }

    @Test
    void disbandingForfeitsEveryOpenWar() {
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender)
                .status(ClanWarStatus.preparing).build();
        when(warRepository.findOpenIdsLedByClanId(attacker.getId())).thenReturn(List.of(war.getId()));
        when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
        List<ClanWarSide> sides = List.of(ClanWarSide.builder().war(war).clan(attacker).team(ClanWarTeam.attack)
                .build());
        when(sideRepository.findByWarId(war.getId())).thenReturn(sides);

        service.forfeitAll(attacker.getId());

        assertThat(war.getOutcome()).isEqualTo(ClanWarOutcome.forfeited);
        verify(scoreGate).refreshAfterCommit();
        verify(aidRepository).cancelPendingForWar(war.getId(), war.getEndedAt());
        verify(eventPublisher).publishEvent(new ClanWarEndedEvent(war.getId()));
        verify(feed).war(war, ClanFeedType.war_ended);
        verify(notifier).warEnded(war, sides);
    }

    @Nested
    class Cap {

        private ClanWar war;
        private final List<ClanWarSide> sides = new ArrayList<>();

        @BeforeEach
        void aLongWar() {
            war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender)
                    .status(ClanWarStatus.active).build();
            lenient().when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
            lenient().when(sideRepository.findByWarId(war.getId())).thenReturn(sides);
        }

        @Test
        void theTeamThatDrainedMoreOfTheOtherWins() {
            when(stakeService.drainedShare(sides, ClanWarTeam.attack)).thenReturn(0.2);
            when(stakeService.drainedShare(sides, ClanWarTeam.defend)).thenReturn(0.5);

            service.endAtCap(war.getId());

            assertThat(war.getOutcome()).isEqualTo(ClanWarOutcome.attacker_won);
        }

        @Test
        void anEvenFightIsADraw() {
            when(stakeService.drainedShare(sides, ClanWarTeam.attack)).thenReturn(0.4);
            when(stakeService.drainedShare(sides, ClanWarTeam.defend)).thenReturn(0.4);

            service.endAtCap(war.getId());

            assertThat(war.getOutcome()).isEqualTo(ClanWarOutcome.drawn);
        }

        @Test
        void aWarThatAlreadyEndedIsLeftAlone() {
            war.setStatus(ClanWarStatus.ended);
            war.setOutcome(ClanWarOutcome.retreated);

            service.endAtCap(war.getId());

            assertThat(war.getOutcome()).isEqualTo(ClanWarOutcome.retreated);
            verify(stakeService, never()).drainedShare(any(), any());
        }
    }

    @Test
    void aClosingSeasonEndsItsOpenWars() {
        UUID seasonId = UUID.randomUUID();
        ClanWar war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender)
                .status(ClanWarStatus.active).build();
        when(warRepository.findOpenIdsBySeasonId(seasonId)).thenReturn(List.of(war.getId()));
        when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));

        service.endSeason(seasonId);

        assertThat(war.getOutcome()).isEqualTo(ClanWarOutcome.season_ended);
    }
}
