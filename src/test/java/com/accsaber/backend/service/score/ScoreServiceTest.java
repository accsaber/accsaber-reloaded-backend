package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.dto.request.score.PluginSubmitRequest;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.staff.JwtService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

        private static final UUID SLOWER_SONG_ID = UUID.randomUUID();

        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private ScoreModifierLinkRepository modifierLinkRepository;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;
        @Mock
        private ModifierRepository modifierRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private MapDifficultyComplexityService mapComplexityService;
        @Mock
        private APCalculationService apCalculationService;
        @Mock
        private StatisticsService statisticsService;
        @Mock
        private RankingService rankingService;
        @Mock
        private XPCalculationService xpCalculationService;
        @Mock
        private MilestoneEvaluationService milestoneEvaluationService;
        @Mock
        private MapDifficultyStatisticsService mapDifficultyStatisticsService;
        @Mock
        private ScoreRankingService scoreRankingService;
        @Mock
        private DuplicateUserService duplicateUserService;
        @Mock
        private com.accsaber.backend.service.skill.SkillService skillService;
        @Mock
        private com.accsaber.backend.service.item.LevelUpAwardService levelUpAwardService;
        @Mock
        private com.accsaber.backend.service.campaign.CampaignEvaluationService campaignEvaluationService;
        @Mock
        private com.accsaber.backend.service.infra.ModifierCacheService modifierCacheService;
        @Mock
        private com.accsaber.backend.service.item.StrangeTrackingService strangeTrackingService;
        @Mock
        private ApplicationEventPublisher eventPublisher;
        @Mock
        private TransactionTemplate transactionTemplate;

        private ScoreService scoreService;

        private MapDifficulty rankedDifficulty;
        private User activeUser;
        private Curve scoreCurve;

        @BeforeEach
        void setUp() {
                scoreService = new ScoreService(scoreRepository, modifierLinkRepository, mapDifficultyRepository,
                                modifierRepository, userRepository, mapComplexityService, apCalculationService,
                                statisticsService, rankingService, xpCalculationService, milestoneEvaluationService,
                                mapDifficultyStatisticsService, scoreRankingService, levelUpAwardService,
                                campaignEvaluationService, modifierCacheService, eventPublisher, transactionTemplate,
                                strangeTrackingService,
                                new ScoreQueryService(scoreRepository, modifierLinkRepository, null, mapDifficultyRepository,
                                                mapComplexityService, xpCalculationService, duplicateUserService, null,
                                                skillService, null),
                                new SubmitNonceService(), new SubmitRateLimitService());
                lenient().when(duplicateUserService.resolvePrimaryUserId(any(Long.class)))
                                .thenAnswer(inv -> inv.getArgument(0));
                scoreCurve = Curve.builder()
                                .id(UUID.randomUUID())
                                .name("Test Score Curve")
                                .type(CurveType.POINT_LOOKUP)
                                .scale(61.0)
                                .shift(-18.0)
                                .build();

                Category category = Category.builder()
                                .id(UUID.randomUUID())
                                .code("true_acc")
                                .name("True Acc")
                                .scoreCurve(scoreCurve)
                                .active(true)
                                .build();

                rankedDifficulty = MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .map(Map.builder().id(UUID.randomUUID()).songName("Song").songHash("hash").build())
                                .category(category)
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(MapDifficultyStatus.RANKED)
                                .maxScore(1_000_000)
                                .active(true)
                                .build();

                activeUser = User.builder()
                                .id(76561198000000001L)
                                .name("TestPlayer")
                                .active(true)
                                .banned(false)
                                .build();
        }

        private SubmitScoreRequest buildRequest(int score) {
                SubmitScoreRequest req = new SubmitScoreRequest();
                req.setUserId(activeUser.getId());
                req.setMapDifficultyId(rankedDifficulty.getId());
                req.setScore(score);
                req.setScoreNoMods(score);
                req.setRank(1);
                req.setRankWhenSet(1);
                return req;
        }

        private Score buildExistingScore(Double ap) {
                return Score.builder()
                                .id(UUID.randomUUID())
                                .user(activeUser)
                                .mapDifficulty(rankedDifficulty)
                                .score(900_000)
                                .scoreNoMods(900_000)
                                .rank(1)
                                .rankWhenSet(1)
                                .ap(ap)
                                .weightedAp(ap)
                                .active(true)
                                .build();
        }

        private void stubCommonMocks(Double rawAp) {
                when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                .thenReturn(Optional.of(rankedDifficulty));
                when(userRepository.findById(activeUser.getId()))
                                .thenReturn(Optional.of(activeUser));
                when(mapComplexityService.findActiveComplexity(rankedDifficulty.getId()))
                                .thenReturn(Optional.of(10.0));
                when(apCalculationService.calculateRawAP(anyDouble(), anyDouble(), any()))
                                .thenReturn(new APResult(rawAp, 0.5));
                lenient().when(xpCalculationService.calculateXpForNewMap(anyDouble(), anyDouble()))
                                .thenReturn(10.0);
                lenient().when(xpCalculationService.calculateXpForImprovement(anyDouble(), any(), anyDouble()))
                                .thenReturn(10.0);
                lenient().when(xpCalculationService.calculateXpForWorseScore())
                                .thenReturn(10.0);
                when(modifierLinkRepository.findByScore_Id(any()))
                                .thenReturn(Collections.emptyList());
                lenient().when(milestoneEvaluationService.evaluateAfterScore(any(), any()))
                                .thenReturn(new MilestoneEvaluationService.EvaluationResult(
                                                Collections.emptyList(), Collections.emptyList()));
                lenient().doAnswer(inv -> {
                        Runnable cb = inv.getArgument(2);
                        if (cb != null)
                                cb.run();
                        return null;
                }).when(rankingService).updateRankingForUserAsync(any(UUID.class), any(Long.class),
                                any(Runnable.class));
                lenient().doAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        Consumer<TransactionStatus> cb = (Consumer<TransactionStatus>) inv.getArgument(0);
                        cb.accept(null);
                        return null;
                }).when(transactionTemplate).executeWithoutResult(any());
        }

        @Nested
        class Submit {

                @Test
                void newScore_noExisting_calculatesAPAndSaves() {
                        Double rawAp = 500.000000;
                        stubCommonMocks(rawAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.empty());
                        Score saved = buildExistingScore(rawAp);
                        when(scoreRepository.saveAndFlush(any())).thenReturn(saved);

                        ScoreResponse response = scoreService.submit(buildRequest(950_000));

                        assertThat(response.getAp()).isEqualByComparingTo(rawAp);
                        verify(statisticsService).recalculate(activeUser.getId(),
                                        rankedDifficulty.getCategory().getId());
                        verify(eventPublisher).publishEvent(any(ScoreSubmittedEvent.class));
                }

                @Test
                void betterScore_supersedesExisting() {
                        Double oldAp = 400.000000;
                        Double newAp = 500.000000;
                        stubCommonMocks(newAp);
                        Score existing = buildExistingScore(oldAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(existing));
                        Score savedNew = buildExistingScore(newAp);
                        when(scoreRepository.saveAndFlush(any())).thenReturn(existing).thenReturn(savedNew);

                        scoreService.submit(buildRequest(960_000));

                        assertThat(existing.isActive()).isFalse();
                        verify(scoreRepository).saveAndFlush(existing);
                        verify(statisticsService).recalculate(activeUser.getId(),
                                        rankedDifficulty.getCategory().getId());
                        verify(rankingService).updateRankingForUserAsync(eq(rankedDifficulty.getCategory().getId()),
                                        eq(activeUser.getId()), any(Runnable.class));
                }

                @Test
                void worseScore_keepsExisting_savesInactiveHistory() {
                        Double oldAp = 600.000000;
                        Double newAp = 500.000000;
                        stubCommonMocks(newAp);
                        Score existing = buildExistingScore(oldAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(existing));

                        ScoreResponse response = scoreService.submit(buildRequest(880_000));

                        assertThat(response.getAp()).isEqualByComparingTo(newAp);
                        assertThat(existing.isActive()).isTrue();
                        verify(statisticsService, never()).recalculate(any(), any());
                        verify(eventPublisher).publishEvent(any(ScoreSubmittedEvent.class));
                }

                @Test
                void duplicateScore_backfillsExistingRow_reEvaluatesMilestones() {
                        Score existing = buildExistingScore(600.000000);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));
                        when(userRepository.findById(activeUser.getId()))
                                        .thenReturn(Optional.of(activeUser));
                        when(scoreRepository.findRecentMatchingPlay(eq(activeUser.getId()),
                                        eq(rankedDifficulty.getId()), eq(900_000), eq(false), eq(false), any(), any(), any(), any()))
                                        .thenReturn(List.of(existing));
                        when(milestoneEvaluationService.evaluateAfterScore(eq(activeUser.getId()), eq(existing)))
                                        .thenReturn(new MilestoneEvaluationService.EvaluationResult(
                                                        Collections.emptyList(), Collections.emptyList()));

                        SubmitScoreRequest request = buildRequest(900_000);
                        request.setBlScoreId(123_456L);
                        request.setStreak115(42);

                        ScoreResponse response = scoreService.submit(request);

                        assertThat(response.getId()).isEqualTo(existing.getId());
                        assertThat(existing.getBlScoreId()).isEqualTo(123_456L);
                        assertThat(existing.getStreak115()).isEqualTo(42);
                        verify(scoreRepository).saveAndFlush(existing);
                        verify(milestoneEvaluationService).evaluateAfterScore(activeUser.getId(), existing);
                        verify(statisticsService, never()).recalculate(any(), any());
                }

                @Test
                void duplicateScore_noFieldChanges_skipsSaveAndMilestoneEval() {
                        Score existing = buildExistingScore(600.000000);
                        existing.setBlScoreId(123_456L);
                        existing.setStreak115(42);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));
                        when(userRepository.findById(activeUser.getId()))
                                        .thenReturn(Optional.of(activeUser));
                        when(scoreRepository.findRecentMatchingPlay(eq(activeUser.getId()),
                                        eq(rankedDifficulty.getId()), eq(900_000), eq(false), eq(false), any(), any(), any(), any()))
                                        .thenReturn(List.of(existing));

                        SubmitScoreRequest request = buildRequest(900_000);
                        request.setBlScoreId(123_456L);
                        request.setStreak115(42);

                        scoreService.submit(request);

                        verify(scoreRepository, never()).saveAndFlush(any());
                        verify(milestoneEvaluationService, never()).evaluateAfterScore(any(), any());
                }

                @Test
                void firstEverPlayOnAMap_countsTowardsStrangeItems() {
                        Double rawAp = 500.000000;
                        stubCommonMocks(rawAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.empty());
                        when(scoreRepository.saveAndFlush(any())).thenReturn(buildExistingScore(rawAp));

                        scoreService.submit(buildRequest(950_000));

                        verify(strangeTrackingService).recordPlay(activeUser.getId());
                }

                @Test
                void improvedScore_countsTowardsStrangeItems() {
                        Double oldAp = 400.000000;
                        Double newAp = 500.000000;
                        stubCommonMocks(newAp);
                        Score existing = buildExistingScore(oldAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(existing));
                        when(scoreRepository.saveAndFlush(any())).thenReturn(existing)
                                        .thenReturn(buildExistingScore(newAp));

                        scoreService.submit(buildRequest(960_000));

                        verify(strangeTrackingService).recordPlay(activeUser.getId());
                }

                @Test
                void worseScore_doesNotCountTowardsStrangeItems() {
                        Double oldAp = 600.000000;
                        Double newAp = 500.000000;
                        stubCommonMocks(newAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(buildExistingScore(oldAp)));

                        scoreService.submit(buildRequest(880_000));

                        verify(strangeTrackingService, never()).recordPlay(any());
                }

                @Test
                void partialAttempt_doesNotCountTowardsStrangeItems() {
                        stubCommonMocks(100.000000);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.empty());
                        when(scoreRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        SubmitScoreRequest request = buildRequest(720_000);
                        request.setPartial(true);

                        scoreService.submit(request);

                        verify(strangeTrackingService, never()).recordPlay(any());
                }

                @Test
                void partialSubmit_noExisting_insertsInactivePartial_awardsXp_evaluatesMilestones() {
                        Double rawAp = 100.000000;
                        stubCommonMocks(rawAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.empty());
                        ArgumentCaptor<Score> savedCaptor = ArgumentCaptor.forClass(Score.class);
                        when(scoreRepository.saveAndFlush(savedCaptor.capture()))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        SubmitScoreRequest request = buildRequest(720_000);
                        request.setPartial(true);

                        scoreService.submit(request);

                        Score saved = savedCaptor.getValue();
                        assertThat(saved.isPartial()).isTrue();
                        assertThat(saved.isActive()).isFalse();
                        assertThat(saved.getSupersedesReason()).isEqualTo("Partial attempt");
                        verify(milestoneEvaluationService).evaluateAfterScore(activeUser.getId(), saved);
                        verify(statisticsService, never()).recalculate(any(), any());
                        verify(scoreRankingService, never()).rankNewScore(any(), any(), any());
                }

                @Test
                void playMatch_looksUpATenSecondWindowAroundTimeSet() {
                        Score existing = buildExistingScore(600.000000);
                        Instant playedAt = Instant.parse("2026-08-03T12:00:00Z");
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));
                        when(userRepository.findById(activeUser.getId()))
                                        .thenReturn(Optional.of(activeUser));
                        when(scoreRepository.findRecentMatchingPlay(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any(),
                                        any(), any()))
                                        .thenReturn(List.of(existing));
                        when(milestoneEvaluationService.evaluateAfterScore(eq(activeUser.getId()), eq(existing)))
                                        .thenReturn(new MilestoneEvaluationService.EvaluationResult(
                                                        Collections.emptyList(), Collections.emptyList()));

                        SubmitScoreRequest request = buildRequest(900_000);
                        request.setTimeSet(playedAt);
                        scoreService.submit(request);

                        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
                        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
                        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
                        verify(scoreRepository).findRecentMatchingPlay(any(), any(), any(), anyBoolean(), anyBoolean(),
                                        since.capture(), from.capture(), to.capture(), any());
                        assertThat(since.getValue()).isEqualTo(playedAt.minusSeconds(10));
                        assertThat(from.getValue()).isEqualTo(playedAt.minusSeconds(10));
                        assertThat(to.getValue()).isEqualTo(playedAt.plusSeconds(10));
                }

                @Test
                void playMatch_windowCannotMatchAnything_forPartialAttempts() {
                        Double rawAp = 100.000000;
                        stubCommonMocks(rawAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.empty());
                        when(scoreRepository.saveAndFlush(any(Score.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        SubmitScoreRequest request = buildRequest(720_000);
                        request.setPartial(true);
                        request.setTimeSet(Instant.parse("2026-08-03T12:00:00Z"));
                        scoreService.submit(request);

                        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
                        verify(scoreRepository).findRecentMatchingPlay(any(), any(), any(), anyBoolean(), anyBoolean(), any(),
                                        any(), to.capture(), any());
                        assertThat(to.getValue()).isBefore(Instant.parse("1970-01-02T00:00:00Z"));
                }

                @Test
                void playMatch_windowCannotMatchAnything_whenTimeSetIsMissing() {
                        Score existing = buildExistingScore(600.000000);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));
                        when(userRepository.findById(activeUser.getId()))
                                        .thenReturn(Optional.of(activeUser));
                        when(scoreRepository.findRecentMatchingPlay(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any(),
                                        any(), any()))
                                        .thenReturn(List.of(existing));

                        scoreService.submit(buildRequest(900_000));

                        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
                        verify(scoreRepository).findRecentMatchingPlay(any(), any(), any(), anyBoolean(), anyBoolean(), any(),
                                        any(), to.capture(), any());
                        assertThat(to.getValue()).isBefore(Instant.parse("1970-01-02T00:00:00Z"));
                }

                @Test
                void partialSubmit_doesNotMergeIntoNonPartialRow() {
                        Double rawAp = 100.000000;
                        stubCommonMocks(rawAp);
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                                        activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(Optional.empty());
                        when(scoreRepository.findRecentMatchingPlay(eq(activeUser.getId()),
                                        eq(rankedDifficulty.getId()), eq(720_000), eq(true), eq(false), any(), any(), any(), any()))
                                        .thenReturn(List.of());
                        when(scoreRepository.saveAndFlush(any(Score.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        SubmitScoreRequest request = buildRequest(720_000);
                        request.setPartial(true);

                        scoreService.submit(request);

                        verify(scoreRepository).findRecentMatchingPlay(eq(activeUser.getId()),
                                        eq(rankedDifficulty.getId()), eq(720_000), eq(true), eq(false), any(), any(), any(), any());
                }

                @ParameterizedTest(name = "score {0} / scoreNoMods {1} is rejected with \"{2}\"")
                @CsvSource({
                                "1000001, 1000001, scoreNoMods",
                                "1000001, 1000000, score exceeds",
                                "0, 0, positive",
                                "0, 950000, score must be positive"
                })
                void outOfBoundsScore_throwsValidationException(int score, int scoreNoMods, String expectedMessage) {
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));

                        SubmitScoreRequest req = buildRequest(scoreNoMods);
                        req.setScore(score);

                        assertThatThrownBy(() -> scoreService.submit(req))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining(expectedMessage);
                }

                @Test
                void unrankedDifficulty_throwsValidationException() {
                        rankedDifficulty.setStatus(MapDifficultyStatus.QUEUE);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));

                        assertThatThrownBy(() -> scoreService.submit(buildRequest(950_000)))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("ranked");
                }

                @Test
                void missingComplexity_throwsValidationException() {
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));
                        when(userRepository.findById(activeUser.getId()))
                                        .thenReturn(Optional.of(activeUser));
                        when(mapComplexityService.findActiveComplexity(rankedDifficulty.getId()))
                                        .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> scoreService.submit(buildRequest(950_000)))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("complexity");
                }

                @Test
                void unknownDifficulty_throwsNotFound() {
                        UUID unknownId = UUID.randomUUID();
                        when(mapDifficultyRepository.findByIdAndActiveTrue(unknownId))
                                        .thenReturn(Optional.empty());
                        SubmitScoreRequest req = buildRequest(950_000);
                        req.setMapDifficultyId(unknownId);

                        assertThatThrownBy(() -> scoreService.submit(req))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }

                @Test
                void bannedUser_throwsValidationException() {
                        activeUser.setBanned(true);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));
                        when(userRepository.findById(activeUser.getId()))
                                        .thenReturn(Optional.of(activeUser));

                        assertThatThrownBy(() -> scoreService.submit(buildRequest(950_000)))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Banned");
                }
        }

        @Nested
        class SubmitPlayerWithBannedModifier {

                private SubmitScoreRequest bannedRequest() {
                        SubmitScoreRequest req = buildRequest(950_000);
                        req.setModifierIds(List.of(SLOWER_SONG_ID));
                        when(modifierCacheService.containsBannedModifier(req.getModifierIds()))
                                        .thenReturn(true);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(rankedDifficulty.getId()))
                                        .thenReturn(Optional.of(rankedDifficulty));
                        return req;
                }

                @Test
                void onRankedMap_recordsCampaignAttempt_insteadOfRankedScore() {
                        SubmitScoreRequest req = bannedRequest();
                        when(campaignEvaluationService.isRecordable(activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(true);
                        when(userRepository.findById(activeUser.getId()))
                                        .thenReturn(Optional.of(activeUser));
                        when(scoreRepository.findRecentMatchingPlay(any(), any(), any(), eq(false), eq(true), any(), any(), any(),
                                        any()))
                                        .thenReturn(Collections.emptyList());
                        when(modifierRepository.findAllById(req.getModifierIds()))
                                        .thenReturn(List.of(slowerSong()));
                        when(scoreRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
                        when(modifierLinkRepository.findByScore_Id(any()))
                                        .thenReturn(Collections.emptyList());

                        scoreService.submitPlayer(req);

                        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
                        verify(scoreRepository).saveAndFlush(captor.capture());
                        Score saved = captor.getValue();
                        assertThat(saved.isActive()).isFalse();
                        assertThat(saved.getSupersedesReason()).isEqualTo("Campaign attempt");
                        assertThat(saved.getAp()).isEqualByComparingTo(0.0);
                        assertThat(saved.getWeightedAp()).isEqualByComparingTo(0.0);
                        assertThat(saved.getXpGained()).isEqualByComparingTo(0.0);
                        assertThat(saved.getRank()).isZero();
                        assertThat(saved.getRankWhenSet()).isZero();
                        verify(apCalculationService, never()).calculateRawAP(anyDouble(), anyDouble(), any());
                        verify(statisticsService, never()).recalculate(any(), any());
                        verify(eventPublisher, never()).publishEvent(any(ScoreSubmittedEvent.class));
                }

                @Test
                void partialAttempt_onCampaignPath_isAcceptedAndDiscarded() {
                        SubmitScoreRequest req = bannedRequest();
                        req.setPartial(true);

                        assertThat(scoreService.submitPlayer(req)).isEmpty();

                        verify(scoreRepository, never()).saveAndFlush(any());
                        verify(campaignEvaluationService, never()).evaluateAfterScore(any(), any());
                        verify(campaignEvaluationService, never()).isRecordable(any(), any());
                }

                @Test
                void zeroScore_onCampaignPath_throwsBeforeRecording() {
                        SubmitScoreRequest req = bannedRequest();
                        req.setScore(0);
                        req.setScoreNoMods(0);

                        assertThatThrownBy(() -> scoreService.submitPlayer(req))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("positive");
                        verify(scoreRepository, never()).saveAndFlush(any());
                        verify(campaignEvaluationService, never()).evaluateAfterScore(any(), any());
                }

                @Test
                void onRankedMap_withoutUnlockedCampaignNode_throws() {
                        SubmitScoreRequest req = bannedRequest();
                        when(campaignEvaluationService.isRecordable(activeUser.getId(), rankedDifficulty.getId()))
                                        .thenReturn(false);

                        assertThatThrownBy(() -> scoreService.submitPlayer(req))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("has this map difficulty unlocked");
                        verify(scoreRepository, never()).saveAndFlush(any());
                }

                private com.accsaber.backend.model.entity.Modifier slowerSong() {
                        return com.accsaber.backend.model.entity.Modifier.builder()
                                        .id(SLOWER_SONG_ID)
                                        .name("Slower Song")
                                        .code("SS")
                                        .multiplier(1.0)
                                        .active(true)
                                        .build();
                }
        }

        @Nested
        class SubmitFromPlugin {

                private ScoreService service;
                private PlayerUserDetails principal;

                @BeforeEach
                void setUp() {
                        service = spy(scoreService);
                        principal = new PlayerUserDetails(activeUser);
                        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> inv
                                        .<TransactionCallback<?>>getArgument(0).doInTransaction(null));
                }

                private PluginSubmitRequest baseRequest() {
                        PluginSubmitRequest r = new PluginSubmitRequest();
                        r.setNonce(UUID.randomUUID().toString());
                        r.setMapDifficultyId(UUID.randomUUID());
                        r.setScore(900_000);
                        r.setScoreNoMods(900_000);
                        r.setTimeSet(Instant.now());
                        return r;
                }

                private void acceptSubmission() {
                        doReturn(Optional.of(ScoreResponse.builder().build())).when(service).submitPlayer(any());
                }

                private SubmitScoreRequest captureSubmitted() {
                        ArgumentCaptor<SubmitScoreRequest> captor = ArgumentCaptor.forClass(SubmitScoreRequest.class);
                        verify(service).submitPlayer(captor.capture());
                        return captor.getValue();
                }

                @Test
                void rejectsWhenPrincipalIsNull() {
                        assertThatThrownBy(() -> service.submitFromPlugin(baseRequest(), null))
                                        .isInstanceOf(UnauthorizedException.class);
                        verify(service, never()).submitPlayer(any());
                }

                @Test
                void rejectsWebScopeToken() {
                        PlayerUserDetails webPrincipal = new PlayerUserDetails(activeUser, null, null,
                                        JwtService.SCOPE_WEB);
                        assertThatThrownBy(() -> service.submitFromPlugin(baseRequest(), webPrincipal))
                                        .isInstanceOf(ForbiddenException.class);
                        verify(service, never()).submitPlayer(any());
                }

                @Test
                void forwardsBannedModifier_soCampaignRoutingCanDecide() {
                        UUID nfId = UUID.randomUUID();
                        UUID noId = UUID.randomUUID();
                        when(modifierCacheService.getModifierCodeToId()).thenReturn(
                                        java.util.Map.of("NF", nfId, "NO", noId));
                        PluginSubmitRequest r = baseRequest();
                        r.setModifierCodes(List.of("NF", "NO"));
                        acceptSubmission();

                        service.submitFromPlugin(r, principal);

                        assertThat(captureSubmitted().getModifierIds()).containsExactlyInAnyOrder(nfId, noId);
                }

                @Test
                void rejectsDuplicateNonce() {
                        PluginSubmitRequest r = baseRequest();
                        acceptSubmission();

                        service.submitFromPlugin(r, principal);

                        assertThatThrownBy(() -> service.submitFromPlugin(r, principal))
                                        .isInstanceOf(ConflictException.class)
                                        .hasMessageContaining("nonce");
                }

                @Test
                void rejectsSecondSubmissionWithinRateLimitWindow() {
                        acceptSubmission();

                        service.submitFromPlugin(baseRequest(), principal);

                        PluginSubmitRequest second = baseRequest();
                        assertThatThrownBy(() -> service.submitFromPlugin(second, principal))
                                        .isInstanceOf(TooManyRequestsException.class)
                                        .hasMessageContaining("60s");
                }

                @Test
                void acceptsHappyPath_passesUserIdFromPrincipal() {
                        acceptSubmission();

                        assertThat(service.submitFromPlugin(baseRequest(), principal)).isPresent();
                        assertThat(captureSubmitted().getUserId()).isEqualTo(activeUser.getId());
                }

                @Test
                void futureTimeSetIsClampedToNow() {
                        PluginSubmitRequest r = baseRequest();
                        r.setTimeSet(Instant.now().plusSeconds(86_400));
                        acceptSubmission();

                        service.submitFromPlugin(r, principal);

                        assertThat(captureSubmitted().getTimeSet()).isBeforeOrEqualTo(Instant.now());
                }

                @Test
                void pastTimeSetFromAnOfflineQueueIsKept() {
                        PluginSubmitRequest r = baseRequest();
                        Instant queued = Instant.now().minusSeconds(7 * 86_400);
                        r.setTimeSet(queued);
                        acceptSubmission();

                        service.submitFromPlugin(r, principal);

                        assertThat(captureSubmitted().getTimeSet()).isEqualTo(queued);
                }

                @Test
                void discardedSubmission_isEmpty() {
                        doReturn(Optional.empty()).when(service).submitPlayer(any());

                        assertThat(service.submitFromPlugin(baseRequest(), principal)).isEmpty();
                }
        }
}
