package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarOrder;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTargeting;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.clan.ChatNotice;
import com.accsaber.backend.service.clan.ClanChatChannel;
import com.accsaber.backend.service.clan.ClanFeed;
import com.accsaber.backend.service.clan.ClanNotifier;
import com.accsaber.backend.service.clan.ClanStrengthService;
import com.accsaber.backend.service.clan.ClanStrengthService.MemberStrength;

@ExtendWith(MockitoExtension.class)
class ClanWarRosterServiceTest {

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
    private ClanWarHitRepository hitRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClanStrengthService strengthService;
    @Mock
    private ClanWarStakeService stakeService;
    @Mock
    private ClanChatChannel chatChannel;
    @Mock
    private ClanWarScoreGate scoreGate;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanWarRosterService service;

    private final Clan attacker = Clan.builder().id(UUID.randomUUID()).build();
    private final Clan defender = Clan.builder().id(UUID.randomUUID()).build();
    private final Clan ally = Clan.builder().id(UUID.randomUUID()).build();
    private ClanWar war;
    private final List<ClanWarSide> sides = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(warRepository.lockOrThrow(any())).thenCallRealMethod();
        service = new ClanWarRosterService(warRepository, sideRepository, participantRepository, userRepository,
                strengthService, stakeService, new ClanWarDamage(hitRepository, clanProperties),
                chatChannel, scoreGate, feed, notifier, clanProperties);
        war = ClanWar.builder().id(UUID.randomUUID()).attackerClan(attacker).defenderClan(defender)
                .ruleset(ClanRuleset.duel).status(ClanWarStatus.preparing).startsAt(Instant.now().minusSeconds(1))
                .build();
        sides.add(ClanWarSide.builder().war(war).clan(attacker).team(ClanWarTeam.attack).stake(5000)
                .stakeRemaining(5000).build());
        sides.add(ClanWarSide.builder().war(war).clan(defender).team(ClanWarTeam.defend).stake(5000)
                .stakeRemaining(5000).build());
        lenient().when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
        lenient().when(sideRepository.findByWarId(war.getId())).thenAnswer(inv -> List.copyOf(sides));
        lenient().when(userRepository.getReferenceById(anyLong()))
                .thenAnswer(inv -> User.builder().id(inv.getArgument(0)).build());
    }

    private void teams(List<MemberStrength> attackers, List<MemberStrength> defenders) {
        when(strengthService.memberStrengths(anyCollection(), any())).thenAnswer(inv -> {
            Collection<UUID> clans = inv.getArgument(0);
            return clans.contains(attacker.getId()) ? attackers : defenders;
        });
    }

    private ClanWarParticipant participant(Long userId, Clan clan, double weight) {
        return ClanWarParticipant.builder().war(war).user(User.builder().id(userId).build()).clan(clan)
                .standingWeight(weight).guard(100).build();
    }

    private void resync() {
        when(warRepository.findRosteredIds(attacker.getId())).thenReturn(List.of(war.getId()));
        service.onMembershipChanged(new ClanMembershipChangedEvent(attacker.getId()));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, ClanWarParticipant> saved() {
        ArgumentCaptor<Collection<ClanWarParticipant>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(participantRepository).saveAll(captor.capture());
        return captor.getValue().stream().collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
    }

    @Test
    void startingSettlesTheStakesAndLetsTheLighterTeamTakeLongerToDrain() {
        teams(List.of(new MemberStrength(attacker.getId(), 1L, 90, 0.6),
                new MemberStrength(attacker.getId(), 2L, 70, 0.4)),
                List.of(new MemberStrength(defender.getId(), 11L, 80, 1.0)));

        service.start(war.getId());

        assertThat(war.getStatus()).isEqualTo(ClanWarStatus.active);
        verify(stakeService).rebalance(sides);
        verify(scoreGate).refreshAfterCommit();
        Map<Long, ClanWarParticipant> participants = saved();
        assertThat(participants).hasSize(3);
        assertThat(participants.get(1L).getStandingWeight()).isCloseTo(8100.0 / 13000, within(1e-12));
        assertThat(participants.get(11L).getStandingWeight()).isCloseTo(6400.0 / 13000, within(1e-12));
        assertThat(participants.get(1L).getClan()).isSameAs(attacker);
        assertThat(participants.get(1L).getTarget()).isNull();
        verify(chatChannel).announce(attacker, ChatNotice.ofWar(ChatEvent.war_started, null, defender, war));
        verify(chatChannel).announce(defender, ChatNotice.ofWar(ChatEvent.war_started, null, attacker, war));
        verify(feed).war(war);
        verify(notifier).warStarted(war, sides);
    }

    @Test
    void lockingThePoolEnrolsBothTeamsBeforeTheWarStarts() {
        war.setStatus(ClanWarStatus.picking);
        war.setPicksDueAt(Instant.now());
        war.setStartsAt(null);
        teams(List.of(new MemberStrength(attacker.getId(), 1L, 90, 1.0)),
                List.of(new MemberStrength(defender.getId(), 11L, 80, 1.0)));

        service.prepare(war);

        assertThat(war.getStatus()).isEqualTo(ClanWarStatus.preparing);
        assertThat(war.getStartsAt()).isBetween(Instant.now().plus(Duration.ofHours(24)).minusSeconds(60),
                Instant.now().plus(Duration.ofHours(48)));
        assertThat(saved()).containsOnlyKeys(1L, 11L);
        verify(stakeService, never()).rebalance(any());
        verify(feed).war(war);
        verify(chatChannel).announce(attacker, ChatNotice.ofWar(ChatEvent.war_preparing, null, defender, war));
        verify(chatChannel).announce(defender, ChatNotice.ofWar(ChatEvent.war_preparing, null, attacker, war));
    }

    @Test
    void theRosterKeepsFollowingTheClansDuringPreparation() {
        war.setStartsAt(Instant.now().plusSeconds(3600));
        when(participantRepository.findByWarId(war.getId())).thenReturn(List.of(participant(1L, attacker, 1.0),
                participant(11L, defender, 1.0)));
        teams(List.of(new MemberStrength(attacker.getId(), 2L, 90, 1.0)),
                List.of(new MemberStrength(defender.getId(), 11L, 80, 1.0)));

        resync();

        Map<Long, ClanWarParticipant> participants = saved();
        assertThat(participants.get(1L).getLeftAt()).isNotNull();
        assertThat(participants.get(2L).getLeftAt()).isNull();
        assertThat(war.getStatus()).isEqualTo(ClanWarStatus.preparing);
    }

    @Test
    void aWarWhosePreparationIsNotOverDoesNotStart() {
        war.setStartsAt(Instant.now().plusSeconds(3600));

        service.start(war.getId());

        assertThat(war.getStatus()).isEqualTo(ClanWarStatus.preparing);
        verify(participantRepository, never()).saveAll(any());
    }

    @Test
    void aCoalitionIsWeighedAsOneTeamAndEveryPlayerKeepsTheirOwnClan() {
        war.setStatus(ClanWarStatus.active);
        sides.add(ClanWarSide.builder().war(war).clan(ally).team(ClanWarTeam.defend).stake(2000)
                .stakeRemaining(2000).build());
        teams(List.of(new MemberStrength(attacker.getId(), 1L, 90, 1.0)),
                List.of(new MemberStrength(defender.getId(), 11L, 80, 0.7),
                        new MemberStrength(ally.getId(), 21L, 60, 0.3)));

        resync();

        Map<Long, ClanWarParticipant> participants = saved();
        assertThat(participants).containsOnlyKeys(1L, 11L, 21L);
        assertThat(participants.get(21L).getClan()).isSameAs(ally);
        assertThat(participants.get(21L).getStandingWeight()).isCloseTo(3600.0 / 10000, within(1e-12));
    }

    @Test
    void aMembershipChangeRetiresLeaversEnrolsJoinersAndUpdatesWeights() {
        war.setStatus(ClanWarStatus.active);
        ClanWarParticipant leaver = participant(1L, attacker, 0.5);
        ClanWarParticipant stayer = participant(2L, attacker, 0.5);
        stayer.setGuard(40);
        ClanWarParticipant enemy = participant(11L, defender, 1.0);
        enemy.setTarget(leaver.getUser());
        enemy.setOrder(ClanWarOrder.target);
        enemy.setOrderedTarget(leaver.getUser());
        when(warRepository.findRosteredIds(attacker.getId())).thenReturn(List.of(war.getId()));
        when(participantRepository.findByWarId(war.getId())).thenReturn(List.of(leaver, stayer, enemy));
        teams(List.of(new MemberStrength(attacker.getId(), 4L, 95, 0.8),
                new MemberStrength(attacker.getId(), 2L, 60, 0.2)),
                List.of(new MemberStrength(defender.getId(), 11L, 80, 1.0)));

        service.onMembershipChanged(new ClanMembershipChangedEvent(attacker.getId()));

        Map<Long, ClanWarParticipant> participants = saved();
        assertThat(participants.get(1L).getLeftAt()).isNotNull();
        assertThat(participants.get(2L).getGuard()).isEqualTo(40);
        assertThat(participants.get(2L).getStandingWeight()).isCloseTo(3600.0 / 12625, within(1e-12));
        assertThat(participants.get(4L).getStandingWeight()).isCloseTo(9025.0 / 12625, within(1e-12));
        assertThat(participants.get(11L)).extracting(ClanWarParticipant::getTarget, ClanWarParticipant::getOrder,
                ClanWarParticipant::getOrderedTarget).containsExactly(null, ClanWarOrder.auto, null);
    }

    @Test
    void aClanThatFellLeavesTheRoster() {
        war.setStatus(ClanWarStatus.active);
        sides.get(1).setStakeRemaining(0);
        ClanWarParticipant fallen = participant(11L, defender, 1.0);
        when(participantRepository.findByWarId(war.getId())).thenReturn(List.of(participant(1L, attacker, 1.0),
                fallen));
        when(strengthService.memberStrengths(anyCollection(), any())).thenAnswer(inv -> {
            Collection<UUID> clans = inv.getArgument(0);
            return clans.contains(attacker.getId()) ? List.of(new MemberStrength(attacker.getId(), 1L, 90, 1.0))
                    : List.of();
        });

        resync();

        assertThat(saved().get(11L).getLeftAt()).isNotNull();
    }

    @Test
    void aPickFarBelowTheChooserSurvivesTheNextSync() {
        war.setStatus(ClanWarStatus.active);
        ClanWarParticipant chooser = participant(1L, attacker, 1.0);
        ClanWarParticipant weak = participant(11L, defender, 1.0);
        chooser.setTarget(weak.getUser());
        when(participantRepository.findByWarId(war.getId())).thenReturn(List.of(chooser, weak));
        teams(List.of(new MemberStrength(attacker.getId(), 1L, 90, 1.0)),
                List.of(new MemberStrength(defender.getId(), 11L, 10, 1.0)));

        resync();

        assertThat(saved().get(1L).getTarget()).isSameAs(weak.getUser());
    }

    @Test
    void withNoPickEachMapPairsWithTheEnemyThePlayWouldHurtMost() {
        ClanWarParticipant me = participant(1L, attacker, 1.0);
        ClanWarParticipant fresh = participant(11L, defender, 0.5);
        ClanWarParticipant worn = participant(12L, defender, 0.5);
        when(participantRepository.findActiveByWarId(war.getId())).thenReturn(List.of(me, fresh, worn));
        when(participantRepository.findOverallSkills(anyCollection()))
                .thenReturn(List.of(skill(1L, 50), skill(11L, 50), skill(12L, 50)));
        when(hitRepository.cycleHits(eq(war.getId()), anyCollection())).thenReturn(Map.of(12L, new ClanWarHitRepository.Streak(2, 0, Map.of(), Map.of())));
        UUID open = UUID.randomUUID();
        UUID played = UUID.randomUUID();
        when(hitRepository.findActiveScores(anyCollection(), anyCollection()))
                .thenReturn(List.of(victimScore(played, UUID.randomUUID(), 12L, 900_000)));

        Map<UUID, ClanWarDamage.Pairing> pairings = service.pairings(war, ClanWarView.of(1L), List.of(open, played));

        assertThat(pairings.get(open).damage()).isEqualTo(10.0);
        assertThat(pairings.get(played).target().getId()).isEqualTo(12L);
        assertThat(pairings.get(played).picked()).isFalse();
    }

    @Test
    void theSweepSpeedsUpEveryRunningReviveByHowOutnumberedTheTeamIs() {
        war.setStatus(ClanWarStatus.active);
        Instant now = Instant.now();
        Instant wipedBefore = now.minus(Duration.ofDays(2));
        List<ClanWarParticipant> broken = List.of(participant(12L, defender, 0.25), participant(13L, defender, 0.25),
                participant(14L, defender, 0.25));
        broken.forEach(p -> p.breakGuard(now.minus(Duration.ofDays(1)), Duration.ofDays(7)));
        List<ClanWarParticipant> roster = new ArrayList<>(broken);
        roster.add(participant(1L, attacker, 1.0));
        roster.add(participant(11L, defender, 0.25));
        when(participantRepository.findActiveByWarId(war.getId())).thenReturn(roster);

        service.restoreGuards(war.getId(), now, wipedBefore);

        assertThat(broken).allSatisfy(p -> {
            assertThat(p.getReviveSpeed()).isEqualTo(3.5);
            assertThat(p.getReviveAt()).isEqualTo(now.plusMillis(Math.round(Duration.ofDays(6).toMillis() / 3.5)));
        });
        verify(participantRepository).restoreGuards(war.getId(), now, clanProperties.getWar().getGuard());
    }

    @Test
    void autoGoesForTheMostStandingPerHitAndReportsTheMapPenalty() {
        ClanWarParticipant me = participant(1L, attacker, 1.0);
        ClanWarParticipant hard = participant(11L, defender, 0.5);
        ClanWarParticipant rich = participant(12L, defender, 0.5);
        when(participantRepository.findActiveByWarId(war.getId())).thenReturn(List.of(me, hard, rich));
        when(participantRepository.findOverallSkills(anyCollection()))
                .thenReturn(List.of(skill(1L, 50), skill(11L, 60), skill(12L, 50)));
        when(stakeService.breakAmount(any(), any(), eq(hard))).thenReturn(10.0);
        when(stakeService.breakAmount(any(), any(), eq(rich))).thenReturn(100.0);
        UUID map = UUID.randomUUID();
        when(hitRepository.cycleHits(eq(war.getId()), anyCollection()))
                .thenReturn(Map.of(12L, new ClanWarHitRepository.Streak(0, 0, Map.of(), Map.of(map, 1L))));
        when(hitRepository.findActiveScores(anyCollection(), anyCollection())).thenReturn(List.of(
                victimScore(map, UUID.randomUUID(), 11L, 900_000), victimScore(map, UUID.randomUUID(), 12L, 900_000)));

        ClanWarDamage.Pairing pairing = service.pairings(war, ClanWarView.of(1L), List.of(map)).get(map);

        assertThat(pairing.target().getId()).isEqualTo(12L);
        assertThat(pairing.mapPenalty()).isEqualTo(0.75);
    }

    @Test
    void aFocusPairsEveryMapWithThatEnemyWithoutCountingAsAPick() {
        ClanWarParticipant me = participant(1L, attacker, 1.0);
        ClanWarParticipant fresh = participant(11L, defender, 0.5);
        ClanWarParticipant worn = participant(12L, defender, 0.5);
        when(participantRepository.findActiveByWarId(war.getId())).thenReturn(List.of(me, fresh, worn));
        when(participantRepository.findOverallSkills(anyCollection()))
                .thenReturn(List.of(skill(1L, 50), skill(11L, 50), skill(12L, 50)));
        when(hitRepository.cycleHits(eq(war.getId()), anyCollection()))
                .thenReturn(Map.of(12L, new ClanWarHitRepository.Streak(2, 0, Map.of(), Map.of())));
        UUID played = UUID.randomUUID();
        when(hitRepository.findActiveScores(anyCollection(), anyCollection())).thenReturn(List.of(
                victimScore(played, UUID.randomUUID(), 11L, 900_000), victimScore(played, UUID.randomUUID(), 12L, 900_000)));

        Map<UUID, ClanWarDamage.Pairing> pairings = service.pairings(war, new ClanWarView(1L, null, 11L),
                List.of(played));

        assertThat(pairings.get(played).target().getId()).isEqualTo(11L);
        assertThat(pairings.get(played).picked()).isFalse();
    }

    @Test
    void viewingAsAnotherClansFighterHidesTheirCommandersOrders() {
        ClanWarParticipant me = participant(1L, attacker, 1.0);
        ClanWarParticipant mate = participant(2L, attacker, 1.0);
        ClanWarParticipant enemy = participant(11L, defender, 1.0);
        mate.setOrder(ClanWarOrder.halt);
        enemy.setOrder(ClanWarOrder.halt);
        when(participantRepository.findActiveByWarId(war.getId())).thenReturn(List.of(me, mate, enemy));
        when(participantRepository.findOverallSkills(anyCollection()))
                .thenReturn(List.of(skill(1L, 50), skill(2L, 50), skill(11L, 50)));
        UUID map = UUID.randomUUID();

        assertThat(service.pairings(war, new ClanWarView(1L, 2L, null), List.of(map))).isEmpty();
        assertThat(service.pairings(war, new ClanWarView(1L, 11L, null), List.of(map))).containsKey(map);
    }

    @Test
    void droppingJustUnderHalfJumpsStraightToDoubleReviveSpeed() {
        war.setStatus(ClanWarStatus.active);
        Instant now = Instant.now();
        List<ClanWarParticipant> roster = new ArrayList<>();
        roster.add(participant(1L, attacker, 1.0));
        for (long id = 11; id <= 20; id++) {
            roster.add(participant(id, defender, 0.1));
        }
        ClanWarParticipant broken = roster.get(1);
        for (ClanWarParticipant p : roster.subList(1, 7)) {
            p.breakGuard(now.minus(Duration.ofDays(1)), Duration.ofDays(7));
        }
        when(participantRepository.findActiveByWarId(war.getId())).thenReturn(roster);

        service.restoreGuards(war.getId(), now, now.minus(Duration.ofDays(2)));

        assertThat(broken.getReviveSpeed()).isCloseTo(2.0 + 1.5 * 0.25, within(1e-9));
    }

    @Test
    void aPickedTargetsPairingCarriesTheDamageOfEachMapWithRepeatsKeepingAShareOfTheStreak() {
        ClanWarParticipant me = participant(1L, attacker, 1.0);
        ClanWarParticipant target = participant(11L, defender, 1.0);
        me.setTargeting(ClanWarTargeting.manual);
        me.setTarget(target.getUser());
        UUID repeat = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        when(participantRepository.findActiveByWarId(war.getId())).thenReturn(List.of(me, target));
        when(participantRepository.findOverallSkills(anyCollection()))
                .thenReturn(List.of(skill(1L, 50), skill(11L, 50)));
        when(hitRepository.cycleHits(eq(war.getId()), anyCollection()))
                .thenReturn(Map.of(11L, new ClanWarHitRepository.Streak(2, 0, Map.of(repeat, 1L), Map.of())));
        when(hitRepository.findActiveScores(anyCollection(), anyCollection())).thenReturn(List.of(
                victimScore(repeat, UUID.randomUUID(), 11L, 900_000), victimScore(fresh, UUID.randomUUID(), 11L, 900_000)));

        Map<UUID, ClanWarDamage.Pairing> pairings = service.pairings(war, ClanWarView.of(1L), List.of(repeat, fresh));

        assertThat(pairings.get(repeat).picked()).isTrue();
        assertThat(pairings.get(repeat).repeatMap()).isTrue();
        assertThat(pairings.get(fresh).repeatMap()).isFalse();
        assertThat(pairings.get(repeat).damage()).isCloseTo(10 * (1 + (1.3 * 1.3 - 1) * 0.4), within(1e-9));
        assertThat(pairings.get(fresh).damage()).isCloseTo(10 * 1.3 * 1.3, within(1e-9));
    }

    @Test
    void theViewerSeesHowManyFullHitsBreakEachEnemyAndWhatIsLeftToSpendOnAMapAlreadyInPreparation() {
        ClanWarParticipant mate = participant(2L, attacker, 0.5);
        ClanWarParticipant enemy = participant(11L, defender, 1.0);
        ClanWarParticipant broken = participant(12L, defender, 1.0);
        broken.setBrokenAt(Instant.now());
        when(warRepository.findById(war.getId())).thenReturn(Optional.of(war));
        when(participantRepository.findPageByWarId(war.getId(), PageRequest.of(0, 50)))
                .thenReturn(new PageImpl<>(List.of(mate, enemy, broken)));
        when(participantRepository.findOverallSkills(anyCollection()))
                .thenReturn(List.of(skill(1L, 40), skill(2L, 40), skill(11L, 80), skill(12L, 80)));
        when(participantRepository.findOpenClanId(war.getId(), 1L)).thenReturn(Optional.of(attacker.getId()));
        when(hitRepository.findCycleHits(war.getId(), List.of(11L))).thenReturn(List.of());
        when(hitRepository.cycleHits(war.getId(), List.of(11L))).thenCallRealMethod();
        UUID map = UUID.randomUUID();
        UUID enemyScore = UUID.randomUUID();
        when(hitRepository.findActiveScores(List.of(map), List.of(11L)))
                .thenReturn(List.of(victimScore(map, enemyScore, 11L, 950_000)));
        when(stakeService.breakAmount(any(), any(), eq(enemy))).thenReturn(40.0);
        when(stakeService.engagement(war, ClanWarTeam.defend)).thenReturn(0.5);

        Map<Long, ClanWarParticipantResponse> rows = service.participants(war.getId(), ClanWarView.of(1L), map, PageRequest.of(0, 50))
                .stream().collect(Collectors.toMap(row -> Long.valueOf(row.player().id()), Function.identity()));

        assertThat(rows.get(2L).strikePreview()).isNull();
        assertThat(rows.get(11L).breakStanding()).isEqualTo(40.0);
        assertThat(rows.get(12L).strikePreview()).isNull();
        assertThat(rows.get(11L).strikePreview()).satisfies(preview -> {
            assertThat(preview.disparity()).isEqualTo(2.0);
            assertThat(preview.damage()).isEqualTo(20.0);
            assertThat(preview.hitsToBreak()).isEqualTo(4);
            assertThat(preview.onMap().targetScore()).isEqualTo(950_000);
            assertThat(preview.standingTaken()).isEqualTo(40.0);
            assertThat(preview.standingGained()).isEqualTo(20.0);
        });
    }

    private static ClanWarHitRepository.VictimScoreView victimScore(UUID map, UUID scoreId, Long userId, int score) {
        return new ClanWarHitRepository.VictimScoreView() {
            public UUID getScoreId() {
                return scoreId;
            }

            public UUID getMapDifficultyId() {
                return map;
            }

            public Long getUserId() {
                return userId;
            }

            public int getScore() {
                return score;
            }
        };
    }

    private static ClanMemberRepository.MemberSkillView skill(Long userId, double value) {
        return new ClanMemberRepository.MemberSkillView() {
            public Long getUserId() {
                return userId;
            }

            public double getSkill() {
                return value;
            }
        };
    }
}
