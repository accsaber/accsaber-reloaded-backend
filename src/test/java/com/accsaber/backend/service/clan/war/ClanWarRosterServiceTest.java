package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
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
import com.accsaber.backend.service.clan.ClanCosmeticService;
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
    private ClanCosmeticService cosmeticService;
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
                strengthService, stakeService, new ClanWarDamage(hitRepository, clanProperties), cosmeticService,
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
        when(strengthService.memberStrengths(anyCollection())).thenAnswer(inv -> {
            Collection<UUID> clans = inv.getArgument(0);
            return clans.contains(attacker.getId()) ? attackers : defenders;
        });
    }

    private ClanWarParticipant participant(Long userId, Clan clan, double weight) {
        return ClanWarParticipant.builder().war(war).user(User.builder().id(userId).build()).clan(clan)
                .standingWeight(weight).guard(100).build();
    }

    private void resync() {
        when(warRepository.findActiveIdsByClanId(attacker.getId())).thenReturn(List.of(war.getId()));
        service.onMembershipChanged(new ClanMembershipChangedEvent(attacker.getId()));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, ClanWarParticipant> saved() {
        ArgumentCaptor<Collection<ClanWarParticipant>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(participantRepository).saveAll(captor.capture());
        return captor.getValue().stream().collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
    }

    @Test
    void startingSettlesTheStakesAndEnrolsBothTeamsWithTheirWeights() {
        teams(List.of(new MemberStrength(attacker.getId(), 1L, 90, 0.6),
                new MemberStrength(attacker.getId(), 2L, 70, 0.4)),
                List.of(new MemberStrength(defender.getId(), 11L, 80, 1.0)));

        service.start(war.getId());

        assertThat(war.getStatus()).isEqualTo(ClanWarStatus.active);
        verify(stakeService).rebalance(sides);
        verify(scoreGate).refreshAfterCommit();
        Map<Long, ClanWarParticipant> participants = saved();
        assertThat(participants).hasSize(3);
        assertThat(participants.get(1L).getStandingWeight()).isEqualTo(0.6);
        assertThat(participants.get(1L).getClan()).isSameAs(attacker);
        assertThat(participants.get(1L).getTarget()).isNull();
        verify(chatChannel).announce(attacker, ChatNotice.ofWar(ChatEvent.war_started, null, defender, war));
        verify(chatChannel).announce(defender, ChatNotice.ofWar(ChatEvent.war_started, null, attacker, war));
        verify(feed).war(war);
        verify(notifier).warStarted(war, sides);
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
        assertThat(participants.get(21L).getStandingWeight()).isEqualTo(0.3);
    }

    @Test
    void aMembershipChangeRetiresLeaversEnrolsJoinersAndUpdatesWeights() {
        war.setStatus(ClanWarStatus.active);
        ClanWarParticipant leaver = participant(1L, attacker, 0.5);
        ClanWarParticipant stayer = participant(2L, attacker, 0.5);
        stayer.setGuard(40);
        ClanWarParticipant enemy = participant(11L, defender, 1.0);
        enemy.setTarget(leaver.getUser());
        when(warRepository.findActiveIdsByClanId(attacker.getId())).thenReturn(List.of(war.getId()));
        when(participantRepository.findByWarId(war.getId())).thenReturn(List.of(leaver, stayer, enemy));
        teams(List.of(new MemberStrength(attacker.getId(), 4L, 95, 0.8),
                new MemberStrength(attacker.getId(), 2L, 60, 0.2)),
                List.of(new MemberStrength(defender.getId(), 11L, 80, 1.0)));

        service.onMembershipChanged(new ClanMembershipChangedEvent(attacker.getId()));

        Map<Long, ClanWarParticipant> participants = saved();
        assertThat(participants.get(1L).getLeftAt()).isNotNull();
        assertThat(participants.get(2L).getGuard()).isEqualTo(40);
        assertThat(participants.get(2L).getStandingWeight()).isEqualTo(0.2);
        assertThat(participants.get(4L).getStandingWeight()).isEqualTo(0.8);
        assertThat(participants.get(11L).getTarget()).isNull();
    }

    @Test
    void aClanThatFellLeavesTheRoster() {
        war.setStatus(ClanWarStatus.active);
        sides.get(1).setStakeRemaining(0);
        ClanWarParticipant fallen = participant(11L, defender, 1.0);
        when(participantRepository.findByWarId(war.getId())).thenReturn(List.of(participant(1L, attacker, 1.0),
                fallen));
        when(strengthService.memberStrengths(anyCollection())).thenAnswer(inv -> {
            Collection<UUID> clans = inv.getArgument(0);
            return clans.contains(attacker.getId()) ? List.of(new MemberStrength(attacker.getId(), 1L, 90, 1.0))
                    : List.of();
        });

        resync();

        assertThat(saved().get(11L).getLeftAt()).isNotNull();
    }

    @Test
    void aPickTooFarBelowTheChooserIsDroppedOnTheNextSync() {
        war.setStatus(ClanWarStatus.active);
        ClanWarParticipant chooser = participant(1L, attacker, 1.0);
        ClanWarParticipant weak = participant(11L, defender, 1.0);
        chooser.setTarget(weak.getUser());
        when(participantRepository.findByWarId(war.getId())).thenReturn(List.of(chooser, weak));
        teams(List.of(new MemberStrength(attacker.getId(), 1L, 90, 1.0)),
                List.of(new MemberStrength(defender.getId(), 11L, 50, 1.0)));

        resync();

        assertThat(saved().get(1L).getTarget()).isNull();
    }

    @Test
    void theViewerSeesHowManyFullHitsBreakEachEnemyAndWhatIsLeftToSpendOnAMap() {
        war.setStatus(ClanWarStatus.active);
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
        when(hitRepository.findActiveScores(map, List.of(11L))).thenReturn(List.of(victimScore(enemyScore, 11L, 950_000)));
        when(hitRepository.findPriorHits(war.getId(), 1L, map)).thenReturn(List.of(prior(11L, enemyScore, true)));

        Map<Long, ClanWarParticipantResponse> rows = service.participants(war.getId(), 1L, map, PageRequest.of(0, 50))
                .stream().collect(Collectors.toMap(row -> Long.valueOf(row.player().id()), Function.identity()));

        assertThat(rows.get(2L).strikePreview()).isNull();
        assertThat(rows.get(12L).strikePreview()).isNull();
        assertThat(rows.get(11L).strikePreview()).satisfies(preview -> {
            assertThat(preview.disparity()).isEqualTo(2.0);
            assertThat(preview.damage()).isEqualTo(30.0);
            assertThat(preview.hitsToBreak()).isEqualTo(3);
            assertThat(preview.onMap().targetScore()).isEqualTo(950_000);
            assertThat(preview.onMap().hitUsed()).isFalse();
            assertThat(preview.onMap().chipUsed()).isTrue();
        });
    }

    @Nested
    class Picks {

        private ClanWarParticipant chooser;
        private ClanWarParticipant enemy;

        @BeforeEach
        void twoFighters() {
            war.setStatus(ClanWarStatus.active);
            chooser = participant(1L, attacker, 1.0);
            enemy = participant(11L, defender, 1.0);
            lenient().when(participantRepository.findActiveByWarId(war.getId())).thenReturn(List.of(chooser, enemy));
            lenient().when(participantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        private void skills(double chooserSkill, double enemySkill) {
            when(participantRepository.findOverallSkills(anyCollection())).thenReturn(List.of(
                    skill(1L, chooserSkill), skill(11L, enemySkill)));
        }

        @Test
        void aPlayerCanPickAnEnemyInsideTheirBand() {
            skills(80, 70);

            service.choose(war.getId(), 1L, 11L);

            assertThat(chooser.getTarget()).isSameAs(enemy.getUser());
        }

        @Test
        void pickingUpwardIsAlwaysAllowed() {
            skills(40, 95);

            service.choose(war.getId(), 1L, 11L);

            assertThat(chooser.getTarget()).isSameAs(enemy.getUser());
        }

        @Test
        void aPlayerCannotPickAnEnemyFarBelowThem() {
            skills(80, 40);

            assertThatThrownBy(() -> service.choose(war.getId(), 1L, 11L)).isInstanceOf(ValidationException.class);
            assertThat(chooser.getTarget()).isNull();
        }

        @Test
        void aTeammateIsNotATarget() {
            assertThatThrownBy(() -> service.choose(war.getId(), 1L, 1L)).isInstanceOf(ValidationException.class);
        }

        @Test
        void sendingNothingClearsThePick() {
            chooser.setTarget(enemy.getUser());

            service.choose(war.getId(), 1L, null);

            assertThat(chooser.getTarget()).isNull();
        }

        @Test
        void onlyFightersCanPick() {
            assertThatThrownBy(() -> service.choose(war.getId(), 99L, 11L)).isInstanceOf(ForbiddenException.class);
        }
    }

    private static ClanWarHitRepository.VictimScoreView victimScore(UUID scoreId, Long userId, int score) {
        return new ClanWarHitRepository.VictimScoreView() {
            public UUID getScoreId() {
                return scoreId;
            }

            public Long getUserId() {
                return userId;
            }

            public int getScore() {
                return score;
            }
        };
    }

    private static ClanWarHitRepository.PriorHitView prior(Long victimId, UUID victimScoreId, boolean chip) {
        return new ClanWarHitRepository.PriorHitView() {
            public Long getVictimId() {
                return victimId;
            }

            public UUID getVictimScoreId() {
                return victimScoreId;
            }

            public boolean isChip() {
                return chip;
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
