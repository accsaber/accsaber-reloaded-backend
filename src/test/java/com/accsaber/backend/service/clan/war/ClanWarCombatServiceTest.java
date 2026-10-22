package com.accsaber.backend.service.clan.war;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository.Streak;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.repository.clan.war.ClanWarSideRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.clan.ChatNotice;
import com.accsaber.backend.service.clan.ClanChatChannel;
import com.accsaber.backend.service.clan.ClanFeed;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanStandingService;
import com.accsaber.backend.service.clan.ClanStrengthService;
import com.accsaber.backend.service.clan.ClanXpAward;
import com.accsaber.backend.service.item.LevelUpAwardService;

@ExtendWith(MockitoExtension.class)
class ClanWarCombatServiceTest {

    private static final UUID MAP = UUID.randomUUID();
    private static final int MAX_SCORE = 1_000_000;

    @Mock
    private ClanFeed feed;
    @Mock
    private ClanWarRepository warRepository;
    @Mock
    private ClanRepository clanRepository;
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
    private ClanWarRosterService rosterService;
    @Mock
    private ClanStrengthService strengthService;
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
    private ClanWarSide redSide;
    private ClanWarSide blueSide;
    private ClanWarParticipant attacker;
    private ClanWarParticipant victim;
    private final List<ClanWarParticipant> roster = new ArrayList<>();
    private final Map<Long, Integer> scores = new LinkedHashMap<>();
    private final Map<Long, Double> skills = new LinkedHashMap<>();
    private final List<ClanWarHit> savedHits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ClanProperties.War rules = clanProperties.getWar();
        rules.setBaseDamage(25.0);
        rules.setMissingScoreMultiplier(0.5);
        rules.setBreakShare(0.2);
        rules.setBreakDecay(0.5);
        rules.setBreakXp(200.0);
        rules.setBreakClanXp(150.0);
        rules.setExtraChipChance(0.0);
        ClanWarStakeService stakeService = new ClanWarStakeService(sideRepository, participantRepository,
                strengthService, standingService, clanProperties);
        service = new ClanWarCombatService(warRepository, clanRepository, sideRepository, participantRepository, hitRepository,
                scoreRepository, mapDifficultyRepository, scoreGate, warService, rosterService, stakeService,
                new ClanWarDamage(hitRepository, clanProperties),
                standingService, levelService, levelUpAwardService, chatChannel, feed, clanProperties,
                transactionTemplate);
        war = ClanWar.builder().id(UUID.randomUUID()).season(season).attackerClan(red).defenderClan(blue)
                .ruleset(ClanRuleset.duel).status(ClanWarStatus.active)
                .startsAt(Instant.now().minusSeconds(3600)).build();
        redSide = ClanWarSide.builder().war(war).clan(red).team(ClanWarTeam.attack).stake(1000.0)
                .stakeRemaining(1000.0).build();
        blueSide = ClanWarSide.builder().war(war).clan(blue).team(ClanWarTeam.defend).stake(1000.0)
                .stakeRemaining(1000.0).build();
        attacker = join(1L, red, 0.5, 50);
        victim = join(11L, blue, 0.5, 50);
        lenient().when(warRepository.findByIdForUpdate(war.getId())).thenReturn(Optional.of(war));
        lenient().when(warRepository.markScored(eq(war.getId()), any())).thenReturn(1);
        lenient().when(sideRepository.findByWarId(war.getId())).thenAnswer(inv -> List.of(redSide, blueSide));
        lenient().when(participantRepository.findActiveByWarId(war.getId())).thenAnswer(inv -> List.copyOf(roster));
        lenient().when(participantRepository.findOverallSkills(anyCollection())).thenAnswer(inv -> skills.entrySet()
                .stream().map(entry -> skill(entry.getKey(), entry.getValue())).toList());
        lenient().when(hitRepository.findActiveScores(eq(List.of(MAP)), anyCollection())).thenAnswer(inv -> scores.entrySet()
                .stream().map(entry -> victimScore(entry.getKey(), entry.getValue())).toList());
        lenient().when(participantRepository.findRecentlyActiveShare(any(), any(), any())).thenReturn(1.0);
        lenient().when(mapDifficultyRepository.findById(MAP))
                .thenReturn(Optional.of(MapDifficulty.builder().id(MAP).maxScore(MAX_SCORE).build()));
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

    private ClanWarParticipant join(Long userId, Clan clan, double weight, double skill) {
        ClanWarParticipant participant = ClanWarParticipant.builder().war(war).user(User.builder().id(userId).build())
                .clan(clan).standingWeight(weight).guard(clanProperties.getWar().getGuard()).build();
        roster.add(participant);
        skills.put(userId, skill);
        return participant;
    }

    private ClanWarCombatService.Play play(int score, boolean active) {
        return play(score, active, Instant.now());
    }

    private ClanWarCombatService.Play play(int score, boolean active, Instant setAt) {
        ScoreResponse response = ScoreResponse.builder().id(UUID.randomUUID()).userId("1").mapDifficultyId(MAP)
                .score(score).active(active).timeSet(setAt).build();
        return ClanWarCombatService.Play.of(response);
    }

    private static ClanWarHitRepository.VictimScoreView victimScore(Long userId, int score) {
        UUID scoreId = new UUID(userId, score);
        return new ClanWarHitRepository.VictimScoreView() {
            public UUID getScoreId() {
                return scoreId;
            }

            public UUID getMapDifficultyId() {
                return MAP;
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

    private static double chip(int victimScore, int playScore, double ratio) {
        double gap = ClanWarCombatService.linear(victimScore, MAX_SCORE) - ClanWarCombatService.linear(playScore,
                MAX_SCORE);
        return Math.max(1.0, 3.0 * (1.0 - gap / 0.3) * ratio * ratio);
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

        @Test
        void aPlaySetBeforeTheWarStartedIsIgnored() {
            service.fight(war.getId(), play(950_000, true, war.getStartsAt().minusSeconds(60)));

            verify(warRepository, never()).markScored(any(), any());
            verify(participantRepository, never()).findActiveByWarId(any());
        }

        @Test
        void aPlayAlreadyScoredInThisWarNeverStrikesAgain() {
            scores.put(11L, 900_000);
            when(warRepository.markScored(eq(war.getId()), any())).thenReturn(0);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).isEmpty();
        }

        @Test
        void theAttackersClanIsLockedBeforeTheWar() {
            when(participantRepository.findOpenClanId(war.getId(), 1L)).thenReturn(Optional.of(red.getId()));

            service.fight(war.getId(), play(950_000, true));

            InOrder order = inOrder(clanRepository, warRepository);
            order.verify(clanRepository).findByIdAndActiveTrueForUpdate(red.getId());
            order.verify(warRepository).findByIdForUpdate(war.getId());
        }
    }

    @Nested
    class Duels {

        @Test
        void autoHitsWhereThePlayDealsTheMostDamageNotTheClosestScore() {
            join(12L, blue, 0.5, 49);
            scores.put(11L, 940_000);
            scores.put(12L, 900_000);
            when(hitRepository.cycleHits(eq(war.getId()), anyCollection())).thenReturn(Map.of(12L, new Streak(3, 0)));

            service.fight(war.getId(), play(950_000, true));

            double damage = 25.0 * Math.pow(1.5, 3) * 49.0 / 50.0;
            assertThat(savedHits).singleElement().satisfies(hit -> {
                assertThat(hit.getVictim().getId()).isEqualTo(12L);
                assertThat(hit.getDamage()).isCloseTo(damage, within(1e-9));
                assertThat(hit.isChip()).isFalse();
            });
            assertThat(attacker.getContribution()).isCloseTo(damage, within(1e-9));
            ChatNotice notice = new ChatNotice(ChatEvent.war_hit, attacker.getUser(), roster.get(2).getUser(), null,
                    war);
            verify(chatChannel).announce(red, notice);
            verify(chatChannel).announce(blue, notice);
        }

        @Test
        void autoTakesABreakOverMoreDamageOnSomeoneElse() {
            ClanWarParticipant low = join(12L, blue, 0.5, 50);
            low.setGuard(10.0);
            scores.put(11L, 900_000);
            scores.put(12L, 900_000);
            when(hitRepository.cycleHits(eq(war.getId()), anyCollection())).thenReturn(Map.of(11L, new Streak(2, 0)));

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits.getFirst()).satisfies(hit -> {
                assertThat(hit.getVictim().getId()).isEqualTo(12L);
                assertThat(hit.isBroke()).isTrue();
            });
        }

        @Test
        void aPickedTargetTakesTheHitEvenWhenAnotherScoreIsCloser() {
            join(12L, blue, 0.5, 50);
            scores.put(11L, 900_000);
            scores.put(12L, 940_000);
            attacker.setTarget(victim.getUser());

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).singleElement().extracting(hit -> hit.getVictim().getId()).isEqualTo(11L);
        }

        @Test
        void aPickTheRunCannotTouchFallsBackToTheClosestScore() {
            join(12L, blue, 0.5, 50);
            scores.put(11L, 999_000);
            scores.put(12L, 940_000);
            attacker.setTarget(victim.getUser());

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).singleElement().extracting(hit -> hit.getVictim().getId()).isEqualTo(12L);
        }

        @Test
        void aPlayThatBeatsNobodyChipsTheClosestScoreAboveIt() {
            scores.put(11L, 960_000);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).singleElement().satisfies(hit -> {
                assertThat(hit.isChip()).isTrue();
                assertThat(hit.getDamage()).isCloseTo(chip(960_000, 950_000, 1.0), within(1e-9));
                assertThat(hit.getVictimScore()).isNotNull();
            });
            verify(chatChannel, never()).announce(any(), any());
        }

        @Test
        void aWeakerPlayerChipsLess() {
            skills.put(1L, 60.0);
            skills.put(11L, 80.0);
            scores.put(11L, 960_000);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits.getFirst().getDamage()).isCloseTo(chip(960_000, 950_000, 0.75), within(1e-9));
        }

        @Test
        void aChipNeverDealsLessThanOneDamage() {
            skills.put(1L, 40.0);
            skills.put(11L, 80.0);
            scores.put(11L, 960_000);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits.getFirst().getDamage()).isEqualTo(1.0);
        }

        @Test
        void aScoreOutsideTheChipWindowIsLeftAlone() {
            scores.put(11L, 999_000);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).isEmpty();
        }

        @Test
        void theSameEnemyScoreCanBeHitAgainByTheNextPlay() {
            scores.put(11L, 900_000);

            service.fight(war.getId(), play(950_000, true));
            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).hasSize(2).allSatisfy(hit -> {
                assertThat(hit.getVictim().getId()).isEqualTo(11L);
                assertThat(hit.isChip()).isFalse();
            });
        }

        @Test
        void anEnemyWithNoScoreOnTheMapIsHitSofter() {
            service.fight(war.getId(), play(950_000, false));

            assertThat(savedHits).singleElement().satisfies(hit -> {
                assertThat(hit.getDamage()).isEqualTo(12.5);
                assertThat(hit.getVictimScore()).isNull();
            });
        }

        @Test
        void aSkillGapNeverScalesAHitOnSomeoneWithNoScore() {
            skills.put(1L, 1.0);
            skills.put(11L, 100.0);

            service.fight(war.getId(), play(950_000, false));

            assertThat(savedHits).singleElement().extracting(ClanWarHit::getDamage).isEqualTo(12.5);
        }

        @Test
        void fullHitsEscalateOnFullHitsOnlyAndSkillGapsScaleThem() {
            victim.setGuard(500.0);
            scores.put(11L, 900_000);
            skills.put(1L, 40.0);
            skills.put(11L, 80.0);
            when(hitRepository.cycleHits(eq(war.getId()), anyCollection())).thenReturn(Map.of(11L, new Streak(2, 0)));

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits.getFirst().getDamage()).isCloseTo(25.0 * 1.5 * 1.5 * 2.0, within(1e-9));
        }

        @Test
        void hitsOnSomeoneWithNoScoreEscalateTheStreakByTheSmallerStep() {
            victim.setGuard(500.0);
            scores.put(11L, 900_000);
            when(hitRepository.cycleHits(eq(war.getId()), anyCollection())).thenReturn(Map.of(11L, new Streak(1, 2)));

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits.getFirst().getDamage()).isCloseTo(25.0 * 1.5 * 1.25 * 1.25, within(1e-9));
        }

        @Test
        void anExtraChipLandsOnAnotherEnemyAboveThePlay() {
            clanProperties.getWar().setExtraChipChance(1.0);
            join(12L, blue, 0.5, 50);
            scores.put(11L, 900_000);
            scores.put(12L, 960_000);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).extracting(hit -> hit.getVictim().getId(), ClanWarHit::isChip)
                    .containsExactly(tuple(11L, false), tuple(12L, true));
        }

        @Test
        void aBrokenEnemyCannotBeHitUntilTheyRecover() {
            victim.setBrokenAt(Instant.now().minusSeconds(60));
            scores.put(11L, 900_000);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).isEmpty();
            verify(levelUpAwardService, never()).addClanXp(anyLong(), anyDouble());
        }

        @Test
        void aRetreatingTeamStopsStrikingButCanStillBeHit() {
            war.setRetreatAt(Instant.now().plusSeconds(3600));
            scores.put(1L, 900_000);
            scores.put(11L, 900_000);

            service.fight(war.getId(), play(950_000, true));
            assertThat(savedHits).isEmpty();

            service.fight(war.getId(), ClanWarCombatService.Play.of(ScoreResponse.builder().id(UUID.randomUUID())
                    .userId("11").mapDifficultyId(MAP).score(950_000).active(true).timeSet(Instant.now()).build()));
            assertThat(savedHits).singleElement().satisfies(hit -> assertThat(hit.getVictim().getId()).isEqualTo(1L));
        }

        @Test
        void playersOfAClanThatFellAreOutOfTheFight() {
            blueSide.setStakeRemaining(0.0);
            scores.put(11L, 900_000);

            service.fight(war.getId(), play(950_000, true));

            assertThat(savedHits).isEmpty();
        }

        @Test
        void aBrokenPlayerGetsTheirGuardBackFromAPoolPlayThatIsNotAPersonalBest() {
            attacker.setGuard(0.0);
            attacker.setBrokenAt(Instant.now().minusSeconds(600));
            scores.put(11L, 999_000);

            service.fight(war.getId(), play(950_000, false));

            assertThat(attacker.getGuard()).isEqualTo(100.0);
            assertThat(attacker.getGuardCycle()).isEqualTo(1);
            assertThat(attacker.getBrokenAt()).isNull();
        }
    }

    @Nested
    class Berserker {

        @BeforeEach
        void berserker() {
            war.setRuleset(ClanRuleset.berserker);
        }

        @Test
        void aPlayHitsEveryUnbrokenEnemyItBeatsAndNeverChips() {
            ClanWarParticipant broken = join(12L, blue, 0.5, 50);
            broken.setBrokenAt(Instant.now().minusSeconds(10));
            join(13L, blue, 0.5, 50);
            join(14L, blue, 0.5, 50);
            scores.put(14L, 960_000);

            service.fight(war.getId(), play(950_000, false));

            assertThat(savedHits).extracting(hit -> hit.getVictim().getId()).containsExactly(11L, 13L);
            assertThat(savedHits).noneMatch(ClanWarHit::isChip);
        }
    }

    @Nested
    class Breaks {

        @BeforeEach
        void aBattleWornVictim() {
            victim.setGuard(10.0);
            victim.setBreaksSuffered(1);
            scores.put(11L, 900_000);
            ClanWarHit earlierChip = ClanWarHit.builder().attacker(User.builder().id(2L).build()).damage(90.0).build();
            lenient().when(hitRepository.findByWar_IdAndVictim_IdAndVictimCycle(war.getId(), 11L, 0))
                    .thenAnswer(inv -> {
                        List<ClanWarHit> cycle = new ArrayList<>(savedHits);
                        cycle.add(earlierChip);
                        return cycle;
                    });
        }

        @Test
        void aBreakTakesTheTeamStakeShareOutOfTheVictimsOwnSlice() {
            service.fight(war.getId(), play(950_000, true));

            ClanWarHit breaking = savedHits.getFirst();
            assertThat(breaking.isBroke()).isTrue();
            assertThat(breaking.getDamage()).isEqualTo(10.0);
            assertThat(breaking.getStandingMoved()).isCloseTo(1000 * 0.2 * 0.5 * 0.5, within(1e-9));
            assertThat(blueSide.getStakeRemaining()).isCloseTo(950.0, within(1e-9));
            assertThat(victim.getBreaksSuffered()).isEqualTo(2);
            String source = breaking.getId().toString();
            verify(standingService).applyAll(season.getId(), Map.of(blue.getId(), -50.0, red.getId(), 50.0),
                    ClanStandingSource.war_break, source);
            verify(levelService).grantXp(red.getId(), new ClanXpAward(150.0, ClanXpSource.war_break, source, true));
            verify(levelUpAwardService).addClanXp(1L, 20.0);
            verify(levelUpAwardService).addClanXp(2L, 180.0);
            assertThat(attacker.getContribution()).isEqualTo(10.0 + 50.0);
            verify(warService, never()).end(any(), any());
            verify(rosterService, never()).sync(any(), any());
            verify(feed).war(war);
            verify(chatChannel).announce(red, new ChatNotice(ChatEvent.war_break, attacker.getUser(), victim.getUser(),
                    null, war));
        }

        @Test
        void aBreakOnACoalitionMemberUsesTheWholeTeamStake() {
            Clan ally = Clan.builder().id(UUID.randomUUID()).build();
            ClanWarSide allySide = ClanWarSide.builder().war(war).clan(ally).team(ClanWarTeam.defend).stake(500.0)
                    .stakeRemaining(500.0).build();
            when(sideRepository.findByWarId(war.getId())).thenReturn(List.of(redSide, blueSide, allySide));

            service.fight(war.getId(), play(950_000, true));

            double fullTeamStake = 1000 + 500 / 0.3;
            assertThat(savedHits.getFirst().getStandingMoved()).isCloseTo(fullTeamStake * 0.2 * 0.5 * 0.5, within(1e-9));
            assertThat(blueSide.getStakeRemaining()).isCloseTo(1000 - fullTeamStake * 0.05, within(1e-9));
            assertThat(allySide.getStakeRemaining()).isEqualTo(500.0);
        }

        @Test
        void aBreakOnAnAllysPlayerOnlyMovesTheAllysShare() {
            Clan ally = Clan.builder().id(UUID.randomUUID()).build();
            ClanWarSide allySide = ClanWarSide.builder().war(war).clan(ally).team(ClanWarTeam.defend).stake(300.0)
                    .stakeRemaining(300.0).build();
            when(sideRepository.findByWarId(war.getId())).thenReturn(List.of(redSide, blueSide, allySide));
            victim.setClan(ally);

            service.fight(war.getId(), play(950_000, true));

            double fullTeamStake = 1000 + 300 / 0.3;
            assertThat(savedHits.getFirst().getStandingMoved())
                    .isCloseTo(fullTeamStake * 0.2 * 0.5 * 0.5 * 0.3, within(1e-9));
            assertThat(allySide.getStakeRemaining()).isCloseTo(300 - fullTeamStake * 0.05 * 0.3, within(1e-9));
            assertThat(blueSide.getStakeRemaining()).isEqualTo(1000.0);
        }

        @Test
        void anEnemyThatBarelyPlaysGivesLessForTheBreak() {
            when(participantRepository.findRecentlyActiveShare(any(), eq("defend"), any())).thenReturn(0.25);

            service.fight(war.getId(), play(950_000, true));

            String source = savedHits.getFirst().getId().toString();
            verify(standingService).applyAll(season.getId(), Map.of(blue.getId(), -50.0, red.getId(), 25.0),
                    ClanStandingSource.war_break, source);
            verify(levelService).grantXp(red.getId(), new ClanXpAward(75.0, ClanXpSource.war_break, source, true));
            verify(levelUpAwardService).addClanXp(1L, 10.0);
            verify(levelUpAwardService).addClanXp(2L, 90.0);
        }

        @Test
        void aClanWhoseSliceRunsOutFallsWhileItsTeamFightsOn() {
            Clan ally = Clan.builder().id(UUID.randomUUID()).build();
            ClanWarSide allySide = ClanWarSide.builder().war(war).clan(ally).team(ClanWarTeam.defend).stake(500.0)
                    .stakeRemaining(500.0).build();
            when(sideRepository.findByWarId(war.getId())).thenReturn(List.of(redSide, blueSide, allySide));
            blueSide.setStakeRemaining(40.0);

            service.fight(war.getId(), play(950_000, true));

            assertThat(blueSide.getStakeRemaining()).isZero();
            assertThat(blueSide.fallen()).isTrue();
            verify(rosterService).sync(war, List.of(redSide, blueSide, allySide));
            verify(warService, never()).end(any(), any());
        }

        @Test
        void aBreakThatDrainsTheLastSliceWinsTheWar() {
            blueSide.setStakeRemaining(40.0);

            service.fight(war.getId(), play(950_000, true));

            assertThat(blueSide.getStakeRemaining()).isZero();
            verify(warService).end(war.getId(), ClanWarOutcome.attacker_won);
        }

        @Test
        void inBerserkerTheBiggerTeamSplitsMoreBreakXpAndContribution() {
            war.setRuleset(ClanRuleset.berserker);
            clanProperties.getWar().setWarSizeExponent(1.0);
            ClanWarParticipant chipper = join(2L, red, 0.5, 50);

            service.fight(war.getId(), play(950_000, true));

            ClanWarHit breaking = savedHits.getFirst();
            assertThat(breaking.getXpAwarded()).isEqualTo(200.0 * 2.0 * 10.0 / 100.0);
            verify(levelUpAwardService).addClanXp(1L, 40.0);
            verify(levelUpAwardService).addClanXp(2L, 360.0);
            assertThat(attacker.getContribution()).isEqualTo(10.0 * 2.0 + 50.0 * 2.0);
            assertThat(chipper.getContribution()).isEqualTo(50.0 * 2.0);
        }
    }
}
