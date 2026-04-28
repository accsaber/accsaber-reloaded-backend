package com.accsaber.backend.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.StatsDiffResponse;
import com.accsaber.backend.model.dto.response.player.UserCategoryStatisticsResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneSetBonusRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.score.APCalculationService;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private UserCategoryStatisticsRepository statisticsRepository;
        @Mock
        private CategoryRepository categoryRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private APCalculationService apCalculationService;
        @Mock
        private OverallStatisticsService overallStatisticsService;
        @Mock
        private UserMilestoneLinkRepository userMilestoneLinkRepository;
        @Mock
        private UserMilestoneSetBonusRepository userMilestoneSetBonusRepository;
        @Mock
        private DuplicateUserService duplicateUserService;
        @Mock
        private com.accsaber.backend.service.skill.SkillService skillService;

        @InjectMocks
        private StatisticsService statisticsService;

        private User user;
        private Category category;
        private Curve weightCurve;

        @BeforeEach
        void setUp() {
                user = User.builder()
                                .id(76561198000000001L)
                                .name("TestPlayer")
                                .country("US")
                                .active(true)
                                .build();

                weightCurve = Curve.builder()
                                .id(UUID.randomUUID())
                                .name("Weight Curve")
                                .type(CurveType.FORMULA)
                                .formula("LOGISTIC_SIGMOID")
                                .xParameterName("k")
                                .xParameterValue(new BigDecimal("0.4"))
                                .yParameterName("y1")
                                .yParameterValue(new BigDecimal("0.1"))
                                .zParameterName("x1")
                                .zParameterValue(new BigDecimal("15"))
                                .build();

                category = Category.builder()
                                .id(UUID.randomUUID())
                                .code("true_acc")
                                .name("True Acc")
                                .weightCurve(weightCurve)
                                .active(true)
                                .build();

                statisticsService.setDuplicateUserService(duplicateUserService);
                lenient().when(duplicateUserService.resolvePrimaryUserId(any(Long.class)))
                                .thenAnswer(inv -> inv.getArgument(0));
                lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
                lenient().when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                .thenReturn(Optional.of(category));
                lenient().when(scoreRepository.sumXpGainedByUserIdSince(any(), any()))
                                .thenReturn(BigDecimal.ZERO);
                lenient().when(userMilestoneLinkRepository.sumMilestoneXpGainedLast24h(any()))
                                .thenReturn(BigDecimal.ZERO);
                lenient().when(userMilestoneSetBonusRepository.sumSetBonusXpGainedLast24h(any()))
                                .thenReturn(BigDecimal.ZERO);
        }

        private Score buildScore(BigDecimal ap, int scoreValue) {
                return buildScore(ap, scoreValue, null);
        }

        private Score buildScore(BigDecimal ap, int scoreValue, BigDecimal xpGained) {
                MapDifficulty diff = MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .map(Map.builder().id(UUID.randomUUID()).songName("Song").songHash("hash").build())
                                .category(category)
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .status(MapDifficultyStatus.RANKED)
                                .maxScore(1_000_000)
                                .active(true)
                                .build();
                return Score.builder()
                                .id(UUID.randomUUID())
                                .user(user)
                                .mapDifficulty(diff)
                                .score(scoreValue)
                                .scoreNoMods(scoreValue)
                                .rank(1)
                                .rankWhenSet(1)
                                .ap(ap)
                                .weightedAp(ap)
                                .xpGained(xpGained)
                                .active(true)
                                .build();
        }

        @Nested
        class Recalculate {

                @Test
                void singleScore_setsFullWeightedAP() {
                        Score score = buildScore(new BigDecimal("500.000000"), 950_000);
                        when(scoreRepository.findActiveByUserAndCategoryOrderByApDesc(user.getId(), category.getId()))
                                        .thenReturn(List.of(score));
                        when(apCalculationService.calculateWeightedAP(score.getAp(), 0, weightCurve))
                                        .thenReturn(new BigDecimal("500.000000"));
                        when(statisticsRepository.findActiveForUpdate(user.getId(),
                                        category.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        UserCategoryStatisticsResponse response = statisticsService.recalculate(user.getId(),
                                        category.getId());

                        assertThat(response.getAp()).isEqualByComparingTo(new BigDecimal("500.000000"));
                        assertThat(response.getRankedPlays()).isEqualTo(1);
                }

                @Test
                void multipleScores_appliesDecayCorrectly() {
                        Score s1 = buildScore(new BigDecimal("500.000000"), 990_000);
                        Score s2 = buildScore(new BigDecimal("400.000000"), 970_000);
                        Score s3 = buildScore(new BigDecimal("300.000000"), 950_000);
                        when(scoreRepository.findActiveByUserAndCategoryOrderByApDesc(user.getId(), category.getId()))
                                        .thenReturn(List.of(s1, s2, s3));
                        when(apCalculationService.calculateWeightedAP(s1.getAp(), 0, weightCurve))
                                        .thenReturn(new BigDecimal("500.000000"));
                        when(apCalculationService.calculateWeightedAP(s2.getAp(), 1, weightCurve))
                                        .thenReturn(new BigDecimal("386.000000"));
                        when(apCalculationService.calculateWeightedAP(s3.getAp(), 2, weightCurve))
                                        .thenReturn(new BigDecimal("279.490000"));
                        when(statisticsRepository.findActiveForUpdate(user.getId(),
                                        category.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        UserCategoryStatisticsResponse response = statisticsService.recalculate(user.getId(),
                                        category.getId());

                        assertThat(response.getRankedPlays()).isEqualTo(3);
                        assertThat(response.getAp()).isEqualByComparingTo(new BigDecimal("1165.490000"));
                }

                @Test
                void existingStats_deactivatedAndNewVersionCreated() {
                        Score score = buildScore(new BigDecimal("500.000000"), 950_000);
                        UserCategoryStatistics existing = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID())
                                        .user(user)
                                        .category(category)
                                        .ap(new BigDecimal("400.000000"))
                                        .rankedPlays(1)
                                        .active(true)
                                        .build();
                        when(scoreRepository.findActiveByUserAndCategoryOrderByApDesc(user.getId(), category.getId()))
                                        .thenReturn(List.of(score));
                        when(apCalculationService.calculateWeightedAP(any(), any(int.class), any()))
                                        .thenReturn(new BigDecimal("500.000000"));
                        when(statisticsRepository.findActiveForUpdate(user.getId(),
                                        category.getId()))
                                        .thenReturn(Optional.of(existing));
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        statisticsService.recalculate(user.getId(), category.getId());

                        assertThat(existing.isActive()).isFalse();
                        ArgumentCaptor<UserCategoryStatistics> captor = ArgumentCaptor
                                        .forClass(UserCategoryStatistics.class);
                        verify(statisticsRepository, times(2)).saveAndFlush(captor.capture());
                        UserCategoryStatistics newStats = captor.getAllValues().stream()
                                        .filter(UserCategoryStatistics::isActive).findFirst().orElseThrow();
                        assertThat(newStats.getSupersedes()).isEqualTo(existing);
                }

                @Test
                void firstScore_noSupersedesLink() {
                        Score score = buildScore(new BigDecimal("500.000000"), 950_000);
                        when(scoreRepository.findActiveByUserAndCategoryOrderByApDesc(user.getId(), category.getId()))
                                        .thenReturn(List.of(score));
                        when(apCalculationService.calculateWeightedAP(any(), any(int.class), any()))
                                        .thenReturn(new BigDecimal("500.000000"));
                        when(statisticsRepository.findActiveForUpdate(user.getId(),
                                        category.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        statisticsService.recalculate(user.getId(), category.getId());

                        ArgumentCaptor<UserCategoryStatistics> captor = ArgumentCaptor
                                        .forClass(UserCategoryStatistics.class);
                        verify(statisticsRepository, times(1)).saveAndFlush(captor.capture());
                        assertThat(captor.getValue().getSupersedes()).isNull();
                        assertThat(captor.getValue().isActive()).isTrue();
                }

                @Test
                void scoreXp_summedFromXpGained() {
                        Score s1 = buildScore(new BigDecimal("500.000000"), 990_000, new BigDecimal("125.500000"));
                        Score s2 = buildScore(new BigDecimal("400.000000"), 970_000, new BigDecimal("80.250000"));
                        when(scoreRepository.findActiveByUserAndCategoryOrderByApDesc(user.getId(), category.getId()))
                                        .thenReturn(List.of(s1, s2));
                        when(apCalculationService.calculateWeightedAP(any(), any(int.class), any()))
                                        .thenReturn(new BigDecimal("450.000000"));
                        when(statisticsRepository.findActiveForUpdate(user.getId(),
                                        category.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        UserCategoryStatisticsResponse response = statisticsService.recalculate(user.getId(),
                                        category.getId());

                        assertThat(response.getScoreXp()).isEqualByComparingTo(new BigDecimal("205.750000"));
                }

                @Test
                void scoreXp_zeroWhenNoXpGained() {
                        Score s1 = buildScore(new BigDecimal("500.000000"), 990_000);
                        when(scoreRepository.findActiveByUserAndCategoryOrderByApDesc(user.getId(), category.getId()))
                                        .thenReturn(List.of(s1));
                        when(apCalculationService.calculateWeightedAP(any(), any(int.class), any()))
                                        .thenReturn(new BigDecimal("500.000000"));
                        when(statisticsRepository.findActiveForUpdate(user.getId(),
                                        category.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        UserCategoryStatisticsResponse response = statisticsService.recalculate(user.getId(),
                                        category.getId());

                        assertThat(response.getScoreXp()).isEqualByComparingTo(BigDecimal.ZERO);
                }

                @Test
                void countForOverallCategory_triggersOverallRecalculate() {
                        Category countForOverallCategory = Category.builder()
                                        .id(UUID.randomUUID())
                                        .code("true_acc")
                                        .name("True Acc")
                                        .weightCurve(weightCurve)
                                        .countForOverall(true)
                                        .active(true)
                                        .build();
                        Score score = buildScore(new BigDecimal("500.000000"), 950_000);
                        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
                        when(categoryRepository.findByIdAndActiveTrue(countForOverallCategory.getId()))
                                        .thenReturn(Optional.of(countForOverallCategory));
                        when(scoreRepository.findActiveByUserAndCategoryOrderByApDesc(
                                        user.getId(), countForOverallCategory.getId()))
                                        .thenReturn(List.of(score));
                        when(apCalculationService.calculateWeightedAP(any(), any(int.class), any()))
                                        .thenReturn(new BigDecimal("500.000000"));
                        when(statisticsRepository.findActiveForUpdate(
                                        user.getId(), countForOverallCategory.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        statisticsService.recalculate(user.getId(), countForOverallCategory.getId());

                        org.mockito.Mockito.verify(overallStatisticsService).recalculate(user.getId(), true);
                }
        }

        @Nested
        class FindByUserAndCategoryCode {

                @Test
                void returnsStatsForMatchingCode() {
                        UserCategoryStatistics stats = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID())
                                        .user(user)
                                        .category(category)
                                        .ap(new BigDecimal("500.000000"))
                                        .scoreXp(new BigDecimal("200.000000"))
                                        .rankedPlays(5)
                                        .active(true)
                                        .build();
                        when(statisticsRepository.findByUser_IdAndCategory_CodeAndActiveTrue(user.getId(), "true_acc"))
                                        .thenReturn(Optional.of(stats));

                        UserCategoryStatisticsResponse response = statisticsService
                                        .findByUserAndCategoryCode(user.getId(), "true_acc");

                        assertThat(response.getAp()).isEqualByComparingTo(new BigDecimal("500.000000"));
                        assertThat(response.getScoreXp()).isEqualByComparingTo(new BigDecimal("200.000000"));
                        assertThat(response.getRankedPlays()).isEqualTo(5);
                }

                @Test
                void throwsWhenNotFound() {
                        when(statisticsRepository.findByUser_IdAndCategory_CodeAndActiveTrue(user.getId(),
                                        "nonexistent"))
                                        .thenReturn(Optional.empty());

                        org.junit.jupiter.api.Assertions.assertThrows(ResourceNotFoundException.class,
                                        () -> statisticsService.findByUserAndCategoryCode(user.getId(), "nonexistent"));
                }
        }

        @Nested
        class FindHistoric {

                @Test
                void returnsVersionsSortedByCreatedAt() {
                        UserCategoryStatistics s1 = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID()).user(user).category(category)
                                        .ap(new BigDecimal("300.000000")).scoreXp(new BigDecimal("100.000000"))
                                        .rankedPlays(3).active(false).build();
                        UserCategoryStatistics s2 = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID()).user(user).category(category)
                                        .ap(new BigDecimal("500.000000")).scoreXp(new BigDecimal("200.000000"))
                                        .rankedPlays(5).active(true).build();

                        when(statisticsRepository
                                        .findHistoricDownsampled(
                                                        org.mockito.ArgumentMatchers.eq(user.getId()),
                                                        org.mockito.ArgumentMatchers.eq("true_acc"),
                                                        any(Instant.class)))
                                        .thenReturn(List.of(s1, s2));

                        List<UserCategoryStatisticsResponse> result = statisticsService.findHistoric(user.getId(),
                                        "true_acc", 7, "d");

                        assertThat(result).hasSize(2);
                        assertThat(result.get(0).getAp()).isEqualByComparingTo(new BigDecimal("300.000000"));
                        assertThat(result.get(1).getAp()).isEqualByComparingTo(new BigDecimal("500.000000"));
                        assertThat(result.get(0).getScoreXp()).isEqualByComparingTo(new BigDecimal("100.000000"));
                        assertThat(result.get(1).getScoreXp()).isEqualByComparingTo(new BigDecimal("200.000000"));
                }

                @Test
                void invalidUnit_throws() {
                        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                                        () -> statisticsService.findHistoric(user.getId(), "true_acc", 7, "x"));
                }
        }

        @Nested
        class ComputeStatsDiff {

                @Test
                void returnsDiffBetweenBaseAndLatest() {
                        UserCategoryStatistics base = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID()).user(user).category(category)
                                        .ap(new BigDecimal("300.000000")).scoreXp(new BigDecimal("100.000000"))
                                        .averageAcc(new BigDecimal("0.950000")).averageAp(new BigDecimal("200.000000"))
                                        .ranking(10).countryRanking(5).rankedPlays(3)
                                        .createdAt(Instant.now().minusSeconds(86400 * 2))
                                        .active(false).build();
                        UserCategoryStatistics latest = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID()).user(user).category(category)
                                        .ap(new BigDecimal("500.000000")).scoreXp(new BigDecimal("250.000000"))
                                        .averageAcc(new BigDecimal("0.970000")).averageAp(new BigDecimal("350.000000"))
                                        .ranking(7).countryRanking(3).rankedPlays(5)
                                        .createdAt(Instant.now())
                                        .active(true).build();

                        when(statisticsRepository.findLatestBeforeLastDay(user.getId(), "true_acc"))
                                        .thenReturn(Optional.of(base));
                        when(statisticsRepository.findMostRecent(user.getId(), "true_acc"))
                                        .thenReturn(Optional.of(latest));
                        when(scoreRepository.sumXpGainedByUserIdSince(any(), any()))
                                        .thenReturn(new BigDecimal("150.000000"));

                        Optional<StatsDiffResponse> result = statisticsService.computeStatsDiff(user.getId(),
                                        "true_acc");

                        assertThat(result).isPresent();
                        StatsDiffResponse diff = result.get();
                        assertThat(diff.getCategoryId()).isEqualTo(category.getId());
                        assertThat(diff.getApDiff()).isEqualByComparingTo(new BigDecimal("200.000000"));
                        assertThat(diff.getScoreXpDiff()).isEqualByComparingTo(new BigDecimal("150.000000"));
                        assertThat(diff.getAverageAccDiff()).isEqualByComparingTo(new BigDecimal("0.020000"));
                        assertThat(diff.getAverageApDiff()).isEqualByComparingTo(new BigDecimal("150.000000"));
                        assertThat(diff.getRankingDiff()).isEqualTo(-3);
                        assertThat(diff.getCountryRankingDiff()).isEqualTo(-2);
                        assertThat(diff.getRankedPlaysDiff()).isEqualTo(2);
                }

                @Test
                void noBaselineBeforeLastDay_returnsEmpty() {
                        when(statisticsRepository.findLatestBeforeLastDay(user.getId(), "true_acc"))
                                        .thenReturn(Optional.empty());

                        Optional<StatsDiffResponse> result = statisticsService.computeStatsDiff(user.getId(),
                                        "true_acc");

                        assertThat(result).isEmpty();
                }

                @Test
                void noMostRecent_returnsEmpty() {
                        UserCategoryStatistics base = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID()).user(user).category(category)
                                        .ap(BigDecimal.ZERO).scoreXp(BigDecimal.ZERO).rankedPlays(0)
                                        .createdAt(Instant.now().minusSeconds(86400 * 2))
                                        .active(false).build();
                        when(statisticsRepository.findLatestBeforeLastDay(user.getId(), "true_acc"))
                                        .thenReturn(Optional.of(base));
                        when(statisticsRepository.findMostRecent(user.getId(), "true_acc"))
                                        .thenReturn(Optional.empty());

                        Optional<StatsDiffResponse> result = statisticsService.computeStatsDiff(user.getId(),
                                        "true_acc");

                        assertThat(result).isEmpty();
                }

                @Test
                void nullableFields_handledGracefully() {
                        UserCategoryStatistics base = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID()).user(user).category(category)
                                        .ap(new BigDecimal("100.000000")).scoreXp(BigDecimal.ZERO)
                                        .ranking(null).countryRanking(null)
                                        .averageAcc(null).averageAp(null).rankedPlays(0)
                                        .createdAt(Instant.now().minusSeconds(86400 * 2))
                                        .active(false).build();
                        UserCategoryStatistics latest = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID()).user(user).category(category)
                                        .ap(new BigDecimal("200.000000")).scoreXp(new BigDecimal("50.000000"))
                                        .ranking(5).countryRanking(2)
                                        .averageAcc(new BigDecimal("0.960000")).averageAp(new BigDecimal("200.000000"))
                                        .rankedPlays(1)
                                        .createdAt(Instant.now())
                                        .active(true).build();

                        when(statisticsRepository.findLatestBeforeLastDay(user.getId(), "true_acc"))
                                        .thenReturn(Optional.of(base));
                        when(statisticsRepository.findMostRecent(user.getId(), "true_acc"))
                                        .thenReturn(Optional.of(latest));

                        Optional<StatsDiffResponse> result = statisticsService.computeStatsDiff(user.getId(),
                                        "true_acc");

                        assertThat(result).isPresent();
                        StatsDiffResponse diff = result.get();
                        assertThat(diff.getApDiff()).isEqualByComparingTo(new BigDecimal("100.000000"));
                        assertThat(diff.getAverageAccDiff()).isNull();
                        assertThat(diff.getAverageApDiff()).isNull();
                        assertThat(diff.getRankingDiff()).isNull();
                        assertThat(diff.getCountryRankingDiff()).isNull();
                }
        }
}
