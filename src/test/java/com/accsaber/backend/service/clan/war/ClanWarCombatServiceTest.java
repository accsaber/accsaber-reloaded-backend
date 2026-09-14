package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.clan.ClanStandingSource;
import com.accsaber.backend.model.entity.clan.ClanXpSource;
import com.accsaber.backend.model.entity.clan.war.ClanRuleset;
import com.accsaber.backend.model.entity.clan.war.ClanWar;
import com.accsaber.backend.model.entity.clan.war.ClanWarHit;
import com.accsaber.backend.model.entity.clan.war.ClanWarOutcome;
import com.accsaber.backend.model.entity.clan.war.ClanWarParticipant;
import com.accsaber.backend.model.entity.clan.war.ClanWarSide;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.clan.ChatNotice;
import com.accsaber.backend.service.clan.ClanChatChannel;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanStandingService;
import com.accsaber.backend.service.clan.ClanXpAward;
import com.accsaber.backend.service.item.LevelUpAwardService;

@ExtendWith(MockitoExtension.class)
class ClanWarCombatServiceTest {

    private static final UUID MAP = UUID.randomUUID();

    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanWarSideRepository sideRepository;
    @Mock
    private ClanWarParticipantRepository participantRepository;
    @Mock
    private ClanWarHitRepository hitRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private MapDifficultyRepository mapDifficultyRepository;
    @Mock
    private ClanWarScoreGate scoreGate;
    @Mock
    private ClanWarService warService;
    @Mock
    private ClanStandingService standingService;
    @Mock
    private ClanLevelService levelService;
    @Mock
    private LevelUpAwardService levelUpAwardService;
    @Mock
    private ClanChatChannel chatChannel;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final ClanProperties clanProperties = new ClanProperties();
    private ClanWarCombatService service;

    private final Clan red = Clan.builder().id(UUID.randomUUID()).build();
    private final Clan blue = Clan.builder().id(UUID.randomUUID()).build();
    private final ClanSeason season = ClanSeason.builder().id(UUID.randomUUID()).build();
    private ClanWar war;
    private ClanWarParticipant attacker;
    private ClanWarParticipant victim;
    private final List<ClanWarHit> savedHits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ClanWarCombatService(warRepository, sideRepository, participantRepository, hitRepository,
                scoreRepository, mapDifficultyRepository, scoreGate, warService, standingService, levelService,
                levelUpAwardService, chatChannel, clanProperties, transactionTemplate);
        war = ClanWar.builder().id(UUID.randomUUID()).season(season).attackerClan(red).defenderClan(blue)
                .ruleset(ClanRuleset.duel).status(ClanWarStatus.active)
                .startsAt(Instant.now().minusSeconds(3600)).build();
        attacker = participant(1L, red, 0.5);
        victim = participant(11L, blue, 0.5);
        attacker.setDuelTarget(victim.getUser());
        lenient().when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
        lenient().when(participantRepository.findActiveByWarId(war.getId())).thenReturn(List.of(attacker, victim));
        lenient().when(mapDifficultyRepository.getReferenceById(any()))
                .thenAnswer(inv -> MapDifficulty.builder().id(inv.getArgument(0)).build());
        lenient().when(scoreRepository.getReferenceById(any()))
                .thenAnswer(inv -> Score.builder().id(inv.getArgument(0)).build());
        lenient().when(hitRepository.saveAndFlush(any())).thenAnswer(inv -> {
            ClanWarHit hit = inv.getArgument(0);
            hit.setId(UUID.randomUUID());
            savedHits.add(hit);
            return hit;
        });
    }

    private ClanWarParticipant participant(Long userId, Clan clan, double weight) {
        return ClanWarParticipant.builder().war(war).user(User.builder().id(userId).build()).clan(clan)
                .standingWeight(weight).guard(clanProperties.getWar().getGuard()).build();
    }

    private ClanWarCombatService.Play play(int score, boolean active) {
        return play(score, active, Instant.now());
    }

    private ClanWarCombatService.Play play(int score, boolean active, Instant setAt) {
        ScoreResponse response = ScoreResponse.builder().id(UUID.randomUUID()).userId("1").mapDifficultyId(MAP)
                .score(score).active(active).timeSet(setAt).build();
        return new ClanWarCombatService.Play(response, 1L, setAt);
    }

    private void victimScore(Long userId, int score) {
        UUID scoreId = UUID.randomUUID();
        when(hitRepository.findActiveScores(eq(MAP), anyCollection())).thenReturn(List.of(
                new ClanWarHitRepository.VictimScoreView() {
                    public UUID getScoreId() {
                        return scoreId;
                    }

                    public Long getUserId() {
                        return userId;
                    }

                    public int getScore() {
                        return score;
                    }
                }));
    }

    private void skills(double attackerSkill, double victimSkill) {
        when(participantRepository.findOverallSkills(anyCollection())).thenReturn(List.of(skill(1L, attackerSkill),
                skill(11L, victimSkill)));
    }

    private ClanMemberRepository.MemberSkillView skill(Long userId, double value) {
        return new ClanMemberRepository.MemberSkillView() {
            public Long getUserId() {
                return userId;
            }

            public double getSkill() {
                return value;
            }
        };
    }

    @Nested
    class Gate {

        @Test
        void aPartialPlayNeverLooksForAWar() {
            service.onScoreSubmitted(new ScoreSubmittedEvent(ScoreResponse.builder().userId("1").mapDifficultyId(MAP)
                    .partial(true).build()));

            verifyNoInteractions(warRepository);
        }

        @Test
        void aPlayTheGateRulesOutNeverLooksForAWar() {
            service.onScoreSubmitted(new ScoreSubmittedEvent(ScoreResponse.builder().userId("1").mapDifficultyId(MAP)
                    .build()));

            verifyNoInteractions(warRepository);
        }

        @Test
        void aPlayThePlayerMightFightWithRunsOneTransactionPerWar() {
            when(scoreGate.mayMatter(1L, MAP)).thenReturn(true);
            when(warRepository.findActiveIdsFighting(1L, MAP)).thenReturn(List.of(war.getId()));
            doAnswer(inv -> {
                inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
                return null;
            }).when(transactionTemplate).executeWithoutResult(any());

            service.onScoreSubmitted(new ScoreSubmittedEvent(ScoreResponse.builder().id(UUID.randomUUID()).userId("1")
                    .mapDifficultyId(MAP).score(900_000).timeSet(Instant.now()).build()));

            verify(warRepository).findByIdForUpdate(war.getId());
        }
    }

    @Nested
    class Hits {

        @Test
        void beatingTheDuelTargetChipsTheirGuardAndCountsAsContribution() {
            victimScore(11L, 900_000);
            skills(50, 50);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).singleElement().satisfies(hit -> {
                assertThat(hit.getDamage()).isEqualTo(25.0);
                assertThat(hit.getGuardAfter()).isEqualTo(75.0);
                assertThat(hit.isBroke()).isFalse();
                assertThat(hit.getVictimScore()).isNotNull();
            });
            assertThat(victim.getGuard()).isEqualTo(75.0);
            assertThat(attacker.getContribution()).isEqualTo(25.0);
            ChatNotice notice = new ChatNotice(ChatEvent.war_hit, attacker.getUser(), victim.getUser(), null, war);
            verify(chatChannel).announce(red, notice);
            verify(chatChannel).announce(blue, notice);
        }

        @Test
        void aPlayBelowTheTargetsScoreDoesNothing() {
            victimScore(11L, 960_000);
            skills(50, 50);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).isEmpty();
        }

        @Test
        void theSameEnemyScoreCanOnlyBeHitOnceByTheSamePlayer() {
            victimScore(11L, 900_000);
            skills(50, 50);
            when(hitRepository.existsByWar_IdAndAttacker_IdAndVictimScore_Id(eq(war.getId()), eq(1L), any()))
                    .thenReturn(true);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).isEmpty();
        }

        @Test
        void anEnemyWithNoScoreOnTheMapIsHitSofter() {
            skills(50, 50);

            service.fight(war.getId(), play(950_000, false));

            assertThat(savedHits).singleElement().satisfies(hit -> {
                assertThat(hit.getDamage()).isEqualTo(12.5);
                assertThat(hit.getVictimScore()).isNull();
            });
        }

        @Test
        void eachHitInTheSameGuardHitsHarderAndSkillGapsScaleIt() {
            victim.setGuard(500.0);
            victimScore(11L, 900_000);
            skills(40, 80);
            when(hitRepository.countByWar_IdAndVictim_IdAndVictimCycle(war.getId(), 11L, 0)).thenReturn(2L);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits.getFirst().getDamage()).isCloseTo(25.0 * 1.5 * 1.5 * 2.0, within(1e-9));
        }

        @Test
        void aPlaySetBeforeTheWarStartedIsIgnored() {
            service.fight(war.getId(), play(950_000, true, war.getStartsAt().minusSeconds(60)));

            verifyNoInteractions(participantRepository);
        }

        @Test
        void berserkerSwingsAtEveryUnbrokenEnemyBelowThePlay() {
            war.setRuleset(ClanRuleset.berserker);
            ClanWarParticipant broken = participant(12L, blue, 0.5);
            broken.setBrokenAt(Instant.now().minusSeconds(10));
            ClanWarParticipant another = participant(13L, blue, 0.5);
            when(participantRepository.findActiveByWarId(war.getId())).thenReturn(List.of(attacker, victim, broken,
                    another));
            skills(50, 50);

            service.fight(war.getId(), play(950_000, false));

            assertThat(savedHits).extracting(hit -> hit.getVictim().getId()).containsExactly(11L, 13L);
        }

        @Test
        void aBrokenPlayerWhoSetsAPersonalBestOnAPoolMapGetsTheirGuardBack() {
            attacker.setGuard(0.0);
            attacker.setBrokenAt(Instant.now().minusSeconds(600));
            victimScore(11L, 960_000);
            skills(50, 50);

            service.fight(war.getId(), play(950_000, true));

            assertThat(attacker.getGuard()).isEqualTo(100.0);
            assertThat(attacker.getGuardCycle()).isEqualTo(1);
            assertThat(attacker.getBrokenAt()).isNull();
        }
    }

    @Nested
    class Breaks {

        private final ClanWarSide blueSide = ClanWarSide.builder().clan(blue).stake(1000.0).stakeRemaining(1000.0)
                .build();

        @BeforeEach
        void aBattleWornVictim() {
            victim.setGuard(10.0);
            victim.setBreaksSuffered(1);
            victimScore(11L, 900_000);
            skills(50, 50);
            lenient().when(sideRepository.findByWar_IdAndClan_Id(war.getId(), blue.getId()))
                    .thenReturn(Optional.of(blueSide));
            ClanWarHit earlierChip = ClanWarHit.builder().attacker(User.builder().id(2L).build()).damage(75.0).build();
            lenient().when(hitRepository.findByWar_IdAndVictim_IdAndVictimCycle(war.getId(), 11L, 0))
                    .thenAnswer(inv -> {
                        List<ClanWarHit> cycle = new ArrayList<>(savedHits);
                        cycle.add(earlierChip);
                        return cycle;
                    });
        }

        @Test
        void aBreakMovesStandingByWeightAndDecayAndPaysEveryoneWhoChipped() {
            service.fight(war.getId(), play(950_000, true));

            ClanWarHit breaking = savedHits.getFirst();
            assertThat(breaking.isBroke()).isTrue();
            assertThat(breaking.getStandingMoved()).isCloseTo(1000 * 0.2 * 0.5 * 0.5, within(1e-9));
            assertThat(blueSide.getStakeRemaining()).isCloseTo(950.0, within(1e-9));
            assertThat(victim.getBrokenAt()).isNotNull();
            assertThat(victim.getBreaksSuffered()).isEqualTo(2);
            String source = breaking.getId().toString();
            verify(standingService).apply(season.getId(), blue.getId(), -50.0, ClanStandingSource.war_break, source);
            verify(standingService).apply(season.getId(), red.getId(), 50.0, ClanStandingSource.war_break, source);
            verify(levelService).grantXp(red.getId(), new ClanXpAward(150.0, ClanXpSource.war_break, source, true));
            verify(levelUpAwardService).addXp(1L, 50.0);
            verify(levelUpAwardService).addXp(2L, 150.0);
            assertThat(attacker.getContribution()).isEqualTo(25.0 + 50.0);
            verify(warService, never()).end(any(), any());
            verify(chatChannel).announce(red, new ChatNotice(ChatEvent.war_break, attacker.getUser(), victim.getUser(),
                    null, war));
        }

        @Test
        void aBreakThatEmptiesTheStakeWinsTheWar() {
            blueSide.setStakeRemaining(40.0);

            service.fight(war.getId(), play(950_000, true));

            assertThat(blueSide.getStakeRemaining()).isZero();
            verify(warService).end(war.getId(), ClanWarOutcome.attacker_won);
        }
    }

    @Test
    void aBrokenDuelTargetCannotBeHitAgainUntilTheyRecover() {
        victim.setBrokenAt(Instant.now().minusSeconds(60));

        service.fight(war.getId(), play(950_000, true));

        assertThat(savedHits).isEmpty();
        verify(hitRepository, never()).countByWar_IdAndVictim_IdAndVictimCycle(any(), anyLong(), anyInt());
        verify(levelUpAwardService, never()).addXp(anyLong(), anyDouble());
    }
}
