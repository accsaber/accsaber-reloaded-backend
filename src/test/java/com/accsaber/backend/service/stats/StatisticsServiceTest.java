package com.accsaber.backend.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
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

                lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
                lenient().when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                .thenReturn(Optional.of(category));
        }

        private Score buildScore(BigDecimal ap, int scoreValue) {
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
                        when(apCalculationService.calculateWeightedAP(score.getAp(), 1, weightCurve))
                                        .thenReturn(new BigDecimal("500.000000"));
                        when(statisticsRepository.findByUser_IdAndCategory_IdAndActiveTrue(user.getId(),
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
                        when(apCalculationService.calculateWeightedAP(s1.getAp(), 1, weightCurve))
                                        .thenReturn(new BigDecimal("500.000000"));
                        when(apCalculationService.calculateWeightedAP(s2.getAp(), 2, weightCurve))
                                        .thenReturn(new BigDecimal("386.000000"));
                        when(apCalculationService.calculateWeightedAP(s3.getAp(), 3, weightCurve))
                                        .thenReturn(new BigDecimal("279.490000"));
                        when(statisticsRepository.findByUser_IdAndCategory_IdAndActiveTrue(user.getId(),
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
                        when(statisticsRepository.findByUser_IdAndCategory_IdAndActiveTrue(user.getId(),
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
                        when(statisticsRepository.findByUser_IdAndCategory_IdAndActiveTrue(user.getId(),
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
                        when(statisticsRepository.findByUser_IdAndCategory_IdAndActiveTrue(
                                        user.getId(), countForOverallCategory.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        statisticsService.recalculate(user.getId(), countForOverallCategory.getId());

                        org.mockito.Mockito.verify(overallStatisticsService).recalculate(user.getId(), true);
                }
        }
}
