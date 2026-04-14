package com.accsaber.backend.service.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;

@ExtendWith(MockitoExtension.class)
class XPReweightServiceTest {

        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private MapDifficultyComplexityService mapComplexityService;
        @Mock
        private XPCalculationService xpCalculationService;

        private XPReweightService service;

        private final Executor directExecutor = Runnable::run;

        private User user;

        @BeforeEach
        void setUp() {
                service = new XPReweightService(scoreRepository, userRepository, mapComplexityService,
                                xpCalculationService);
                try {
                        var field = XPReweightService.class.getDeclaredField("backfillExecutor");
                        field.setAccessible(true);
                        field.set(service, directExecutor);
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }
                user = User.builder().id(42L).name("TestPlayer").build();
        }

        @Nested
        class ReweightScoresForDifficulty {

                @Test
                void firstScoreGetsNewMapXp() {
                        UUID diffId = UUID.randomUUID();
                        Curve scoreCurve = Curve.builder().id(UUID.randomUUID()).build();
                        Category category = Category.builder().id(UUID.randomUUID()).scoreCurve(scoreCurve).build();
                        MapDifficulty diff = MapDifficulty.builder()
                                        .id(diffId).category(category).maxScore(1000000).build();

                        Score score = Score.builder()
                                        .id(UUID.randomUUID()).score(950000).mapDifficulty(diff)
                                        .user(user).active(true).build();

                        when(scoreRepository.findAllByDifficultyOrderedByUserAndTime(diffId))
                                        .thenReturn(List.of(score));
                        when(mapComplexityService.findActiveComplexity(diffId))
                                        .thenReturn(Optional.of(new BigDecimal("8.0")));
                        when(xpCalculationService.calculateXpForNewMap(any(), any()))
                                        .thenReturn(new BigDecimal("50.000000"));

                        int count = service.reweightScoresForDifficulty(diffId);

                        assertThat(count).isEqualTo(1);
                        assertThat(score.getXpGained()).isEqualByComparingTo(new BigDecimal("50.000000"));
                        verify(scoreRepository).saveAll(any());
                }

                @Test
                void improvementGetsImprovementXp() {
                        UUID diffId = UUID.randomUUID();
                        Curve scoreCurve = Curve.builder().id(UUID.randomUUID()).build();
                        Category category = Category.builder().id(UUID.randomUUID()).scoreCurve(scoreCurve).build();
                        MapDifficulty diff = MapDifficulty.builder()
                                        .id(diffId).category(category).maxScore(1000000).build();

                        Score first = Score.builder()
                                        .id(UUID.randomUUID()).score(800000).mapDifficulty(diff)
                                        .user(user).active(false).build();
                        Score improvement = Score.builder()
                                        .id(UUID.randomUUID()).score(950000).mapDifficulty(diff)
                                        .user(user).active(true).supersedesReason("Score improved").build();

                        when(scoreRepository.findAllByDifficultyOrderedByUserAndTime(diffId))
                                        .thenReturn(List.of(first, improvement));
                        when(mapComplexityService.findActiveComplexity(diffId))
                                        .thenReturn(Optional.of(new BigDecimal("8.0")));
                        when(xpCalculationService.calculateXpForNewMap(any(), any()))
                                        .thenReturn(new BigDecimal("50.000000"));
                        when(xpCalculationService.calculateXpForImprovement(any(), any(), any()))
                                        .thenReturn(new BigDecimal("75.000000"));

                        int count = service.reweightScoresForDifficulty(diffId);

                        assertThat(count).isEqualTo(2);
                        assertThat(first.getXpGained()).isEqualByComparingTo(new BigDecimal("50.000000"));
                        assertThat(improvement.getXpGained()).isEqualByComparingTo(new BigDecimal("75.000000"));
                }

                @Test
                void worseScoreGetsWorseXp() {
                        UUID diffId = UUID.randomUUID();
                        Curve scoreCurve = Curve.builder().id(UUID.randomUUID()).build();
                        Category category = Category.builder().id(UUID.randomUUID()).scoreCurve(scoreCurve).build();
                        MapDifficulty diff = MapDifficulty.builder()
                                        .id(diffId).category(category).maxScore(1000000).build();

                        Score first = Score.builder()
                                        .id(UUID.randomUUID()).score(950000).mapDifficulty(diff)
                                        .user(user).active(true).build();
                        Score worse = Score.builder()
                                        .id(UUID.randomUUID()).score(800000).mapDifficulty(diff)
                                        .user(user).active(false).supersedesReason("Worse score").build();

                        when(scoreRepository.findAllByDifficultyOrderedByUserAndTime(diffId))
                                        .thenReturn(List.of(first, worse));
                        when(mapComplexityService.findActiveComplexity(diffId))
                                        .thenReturn(Optional.of(new BigDecimal("8.0")));
                        when(xpCalculationService.calculateXpForNewMap(any(), any()))
                                        .thenReturn(new BigDecimal("50.000000"));
                        when(xpCalculationService.calculateXpForWorseScore())
                                        .thenReturn(new BigDecimal("25.000000"));

                        service.reweightScoresForDifficulty(diffId);

                        assertThat(first.getXpGained()).isEqualByComparingTo(new BigDecimal("50.000000"));
                        assertThat(worse.getXpGained()).isEqualByComparingTo(new BigDecimal("25.000000"));
                }

                @Test
                void complexityReweightGetsWorseXp() {
                        UUID diffId = UUID.randomUUID();
                        Curve scoreCurve = Curve.builder().id(UUID.randomUUID()).build();
                        Category category = Category.builder().id(UUID.randomUUID()).scoreCurve(scoreCurve).build();
                        MapDifficulty diff = MapDifficulty.builder()
                                        .id(diffId).category(category).maxScore(1000000).build();

                        Score first = Score.builder()
                                        .id(UUID.randomUUID()).score(950000).mapDifficulty(diff)
                                        .user(user).active(false).build();
                        Score reweighted = Score.builder()
                                        .id(UUID.randomUUID()).score(950000).mapDifficulty(diff)
                                        .user(user).active(true).supersedesReason("Complexity reweight").build();

                        when(scoreRepository.findAllByDifficultyOrderedByUserAndTime(diffId))
                                        .thenReturn(List.of(first, reweighted));
                        when(mapComplexityService.findActiveComplexity(diffId))
                                        .thenReturn(Optional.of(new BigDecimal("8.0")));
                        when(xpCalculationService.calculateXpForNewMap(any(), any()))
                                        .thenReturn(new BigDecimal("50.000000"));
                        when(xpCalculationService.calculateXpForWorseScore())
                                        .thenReturn(new BigDecimal("25.000000"));

                        service.reweightScoresForDifficulty(diffId);

                        assertThat(first.getXpGained()).isEqualByComparingTo(new BigDecimal("50.000000"));
                        assertThat(reweighted.getXpGained()).isEqualByComparingTo(new BigDecimal("25.000000"));
                }

                @Test
                void returnsZeroForEmptyDifficulty() {
                        UUID diffId = UUID.randomUUID();
                        when(scoreRepository.findAllByDifficultyOrderedByUserAndTime(diffId))
                                        .thenReturn(List.of());

                        int count = service.reweightScoresForDifficulty(diffId);

                        assertThat(count).isZero();
                        verify(scoreRepository, never()).saveAll(any());
                }

                @Test
                void returnsZeroForNullCategory() {
                        UUID diffId = UUID.randomUUID();
                        MapDifficulty diff = MapDifficulty.builder()
                                        .id(diffId).category(null).maxScore(1000000).build();
                        Score score = Score.builder()
                                        .id(UUID.randomUUID()).score(900000).mapDifficulty(diff)
                                        .user(user).active(true).build();

                        when(scoreRepository.findAllByDifficultyOrderedByUserAndTime(diffId))
                                        .thenReturn(List.of(score));

                        int count = service.reweightScoresForDifficulty(diffId);

                        assertThat(count).isZero();
                        verify(scoreRepository, never()).saveAll(any());
                }

                @Test
                void returnsZeroForZeroMaxScore() {
                        UUID diffId = UUID.randomUUID();
                        Curve scoreCurve = Curve.builder().id(UUID.randomUUID()).build();
                        Category category = Category.builder().id(UUID.randomUUID()).scoreCurve(scoreCurve).build();
                        MapDifficulty diff = MapDifficulty.builder()
                                        .id(diffId).category(category).maxScore(0).build();
                        Score score = Score.builder()
                                        .id(UUID.randomUUID()).score(900000).mapDifficulty(diff)
                                        .user(user).active(true).build();

                        when(scoreRepository.findAllByDifficultyOrderedByUserAndTime(diffId))
                                        .thenReturn(List.of(score));

                        int count = service.reweightScoresForDifficulty(diffId);

                        assertThat(count).isZero();
                        verify(scoreRepository, never()).saveAll(any());
                }
        }

        @Nested
        class ReweightAllScores {

                @Test
                void parallelizesAcrossDifficulties() {
                        UUID diffId = UUID.randomUUID();
                        Curve scoreCurve = Curve.builder().id(UUID.randomUUID()).build();
                        Category category = Category.builder().id(UUID.randomUUID()).scoreCurve(scoreCurve).build();
                        MapDifficulty diff = MapDifficulty.builder()
                                        .id(diffId).category(category).maxScore(1000000).build();

                        Score score = Score.builder()
                                        .id(UUID.randomUUID()).score(950000).mapDifficulty(diff)
                                        .user(user).active(true).build();

                        when(scoreRepository.findDistinctMapDifficultyIds()).thenReturn(List.of(diffId));
                        when(scoreRepository.findAllByDifficultyOrderedByUserAndTime(diffId))
                                        .thenReturn(List.of(score));
                        when(mapComplexityService.findActiveComplexity(diffId))
                                        .thenReturn(Optional.of(new BigDecimal("8.0")));
                        when(xpCalculationService.calculateXpForNewMap(any(), any()))
                                        .thenReturn(new BigDecimal("50.000000"));

                        service.reweightAllScores();

                        verify(scoreRepository).saveAll(any());
                        verify(xpCalculationService).evictXpCurveCache();
                }

                @Test
                void skipsDifficultyWithNoScores() {
                        UUID diffId = UUID.randomUUID();

                        when(scoreRepository.findDistinctMapDifficultyIds()).thenReturn(List.of(diffId));
                        when(scoreRepository.findAllByDifficultyOrderedByUserAndTime(diffId))
                                        .thenReturn(List.of());

                        service.reweightAllScores();

                        verify(scoreRepository, never()).saveAll(any());
                }
        }

        @Nested
        class RecalculateTotalXpForAllUsers {

                @Test
                void callsBulkRecalculation() {
                        service.recalculateTotalXpForAllUsers();

                        verify(userRepository).recalculateTotalXpForAllActiveUsers();
                }
        }
}
