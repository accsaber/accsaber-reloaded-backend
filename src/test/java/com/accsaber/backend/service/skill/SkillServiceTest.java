package com.accsaber.backend.service.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.SkillProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.SkillResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategorySkill;
import com.accsaber.backend.model.entity.user.UserCategorySkillSnapshot;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategorySkillRepository;
import com.accsaber.backend.repository.user.UserCategorySkillSnapshotRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.score.APCalculationService;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    private static final Long USER_ID = 76561198000000123L;
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID OVERALL_ID = UUID.randomUUID();
    private static final String CATEGORY_CODE = "true_acc";

    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserCategoryStatisticsRepository statsRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private UserCategorySkillRepository skillRepository;
    @Mock
    private UserCategorySkillSnapshotRepository snapshotRepository;
    @Mock
    private APCalculationService apCalculationService;

    private SkillProperties skillProperties;
    private SkillService skillService;

    @BeforeEach
    void setUp() {
        skillProperties = new SkillProperties();
        skillService = new SkillService(userRepository, categoryRepository, statsRepository,
                scoreRepository, skillRepository, snapshotRepository, apCalculationService,
                skillProperties);
    }

    @Nested
    class MathPrimitives {

        @Test
        void sigmoidScoreCentersAt50() {
            assertThat(skillService.sigmoidScore(750, 750, 90)).isCloseTo(50.0, within(0.001));
        }

        @Test
        void sigmoidScoreSaturatesNear100AtHighRaw() {
            assertThat(skillService.sigmoidScore(1100, 750, 90)).isGreaterThan(95.0);
        }

        @Test
        void harmonicMeanIsZeroWhenEitherIsZero() {
            assertThat(skillService.harmonicMean(0, 95)).isEqualTo(0);
            assertThat(skillService.harmonicMean(95, 0)).isEqualTo(0);
        }

        @Test
        void harmonicMeanCrushesLowPartner() {
            assertThat(skillService.harmonicMean(5, 95)).isLessThan(15.0);
        }

        @Test
        void rankScoreOneIsHundred() {
            assertThat(skillService.rankScore(1, 125000)).isEqualTo(100.0);
        }

        @Test
        void rankScoreShowsTremendousGapAtTop() {
            double top5 = skillService.rankScore(5, 125000);
            double top25 = skillService.rankScore(25, 125000);
            double top100 = skillService.rankScore(100, 125000);
            assertThat(top5).isGreaterThan(90.0);
            assertThat(top25).isLessThan(top5).isGreaterThan(80.0);
            assertThat(top100).isLessThan(top25).isGreaterThan(70.0);
        }

        @Test
        void rankScoreNullWhenNoRank() {
            assertThat(skillService.rankScore(null, 125000)).isEqualTo(0);
            assertThat(skillService.rankScore(0, 125000)).isEqualTo(0);
        }

        @Test
        void relativePeakFactorIsOneWhenPlayerHoldsCategoryMax() {
            Category c = Category.builder().id(CATEGORY_ID).code(CATEGORY_CODE).name("True Acc").build();
            when(scoreRepository.findMaxApInCategory(CATEGORY_ID)).thenReturn(BigDecimal.valueOf(1100));

            assertThat(skillService.relativePeakFactor(BigDecimal.valueOf(1100), c)).isEqualTo(1.0);
        }

        @Test
        void relativePeakFactorScalesByRatioToTheAlpha() {
            Category c = Category.builder().id(CATEGORY_ID).code(CATEGORY_CODE).name("True Acc").build();
            when(scoreRepository.findMaxApInCategory(CATEGORY_ID)).thenReturn(BigDecimal.valueOf(1100));

            double factor = skillService.relativePeakFactor(BigDecimal.valueOf(1000), c);
            assertThat(factor).isCloseTo(Math.pow(1000.0 / 1100.0, 0.5), within(0.0001));
        }

        @Test
        void relativePeakFactorIsOneWhenCategoryHasNoTopAp() {
            Category c = Category.builder().id(CATEGORY_ID).code(CATEGORY_CODE).name("True Acc").build();
            when(scoreRepository.findMaxApInCategory(CATEGORY_ID)).thenReturn(null);

            assertThat(skillService.relativePeakFactor(BigDecimal.valueOf(500), c)).isEqualTo(1.0);
        }

        @Test
        void relativePeakFactorIsZeroWhenPlayerHasNoTopAp() {
            Category c = Category.builder().id(CATEGORY_ID).code(CATEGORY_CODE).name("True Acc").build();

            assertThat(skillService.relativePeakFactor(BigDecimal.ZERO, c)).isEqualTo(0);
        }
    }

    @Nested
    class ReadFlow {

        @Test
        void returnsRowsFromRepository() {
            mockUser();
            UserCategorySkill row = persistedSkill(category(CATEGORY_CODE, "True Acc"), 87.5, 95);
            when(skillRepository.findByUserIdActive(USER_ID)).thenReturn(List.of(row));

            SkillResponse response = skillService.computeSkillForUser(USER_ID, null);

            assertThat(response.getSkills()).hasSize(1);
            assertThat(response.getSkills().get(0).getSkillLevel()).isEqualTo(87.5);
            verify(apCalculationService, never()).calculateRawApForOneWeightedGain(any(), any());
        }

        @Test
        void lazyUpsertsWhenEmptyThenReads() {
            mockUser();
            Category cat = category(CATEGORY_CODE, "True Acc");
            UserCategorySkill row = persistedSkill(cat, 50.0, 70);
            when(skillRepository.findByUserIdActive(USER_ID))
                    .thenReturn(List.of())
                    .thenReturn(List.of(row));
            when(categoryRepository.findByActiveTrue()).thenReturn(List.of(cat));
            mockStats(50, BigDecimal.valueOf(900));
            mockActivePlayers(125000);
            when(apCalculationService.calculateRawApForOneWeightedGain(any(), any()))
                    .thenReturn(BigDecimal.valueOf(800));
            when(skillRepository.findByUserIdAndCategoryId(eq(USER_ID), any())).thenReturn(Optional.empty());
            when(skillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SkillResponse response = skillService.computeSkillForUser(USER_ID, null);

            assertThat(response.getSkills()).hasSize(1);
            verify(skillRepository, times(2)).findByUserIdActive(USER_ID);
            verify(skillRepository).save(any());
        }

        @Test
        void singleCategoryReturnsThatRow() {
            mockUser();
            mockCategoryFiltered();
            UserCategorySkill row = persistedSkill(category(CATEGORY_CODE, "True Acc"), 75.0, 80);
            when(skillRepository.findByUserIdAndCategoryId(USER_ID, CATEGORY_ID)).thenReturn(Optional.of(row));

            SkillResponse response = skillService.computeSkillForUser(USER_ID, CATEGORY_CODE);

            assertThat(response.getSkills()).hasSize(1);
            assertThat(response.getSkills().get(0).getCategoryCode()).isEqualTo(CATEGORY_CODE);
        }

        @Test
        void unknownUserThrows() {
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> skillService.computeSkillForUser(USER_ID, CATEGORY_CODE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class UpsertFlow {

        @Test
        void persistsSingleCategoryAndOverall() {
            mockUser();
            Category cat = category(CATEGORY_CODE, "True Acc");
            Category overall = Category.builder().id(OVERALL_ID).code("overall").name("Overall").build();
            when(categoryRepository.findByIdAndActiveTrue(CATEGORY_ID)).thenReturn(Optional.of(cat));
            when(categoryRepository.findByCodeAndActiveTrue("overall")).thenReturn(Optional.of(overall));
            mockStats(2, BigDecimal.valueOf(1100));
            mockActivePlayers(125000);
            when(statsRepository.findByUser_IdAndCategory_IdAndActiveTrue(USER_ID, OVERALL_ID))
                    .thenReturn(Optional.empty());
            when(statsRepository.countActivePlayersInCategory(OVERALL_ID)).thenReturn(125000L);
            when(apCalculationService.calculateRawApForOneWeightedGain(any(), any()))
                    .thenReturn(BigDecimal.valueOf(1070));
            when(skillRepository.findByUserIdAndCategoryId(any(), any())).thenReturn(Optional.empty());
            when(skillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(skillRepository.findByUserIdForOverall(USER_ID)).thenReturn(List.of(
                    persistedSkill(cat, 95.0, 95)));

            skillService.upsertSkill(USER_ID, CATEGORY_ID);

            ArgumentCaptor<UserCategorySkill> captor = ArgumentCaptor.forClass(UserCategorySkill.class);
            verify(skillRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues()).extracting(s -> s.getCategory().getCode())
                    .containsExactly(CATEGORY_CODE, "overall");
        }

        @Test
        void overallIsAverageOfContributors() {
            mockUser();
            Category overall = Category.builder().id(OVERALL_ID).code("overall").name("Overall").build();
            when(categoryRepository.findByIdAndActiveTrue(OVERALL_ID)).thenReturn(Optional.of(overall));
            when(statsRepository.findByUser_IdAndCategory_IdAndActiveTrue(USER_ID, OVERALL_ID))
                    .thenReturn(Optional.empty());
            when(statsRepository.countActivePlayersInCategory(OVERALL_ID)).thenReturn(125000L);
            UserCategorySkill a = persistedSkill(category("a", "A"), 80.0, 70);
            UserCategorySkill b = persistedSkill(category("b", "B"), 60.0, 60);
            when(skillRepository.findByUserIdForOverall(USER_ID)).thenReturn(List.of(a, b));
            when(skillRepository.findByUserIdAndCategoryId(USER_ID, OVERALL_ID)).thenReturn(Optional.empty());
            when(skillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            skillService.upsertSkill(USER_ID, OVERALL_ID);

            ArgumentCaptor<UserCategorySkill> captor = ArgumentCaptor.forClass(UserCategorySkill.class);
            verify(skillRepository).save(captor.capture());
            assertThat(captor.getValue().getSkillLevel().doubleValue()).isCloseTo(70.0, within(0.01));
        }

        @Test
        void overallWithNoContributorsFallsBackToRankOnly() {
            mockUser();
            Category overall = Category.builder().id(OVERALL_ID).code("overall").name("Overall").build();
            when(categoryRepository.findByIdAndActiveTrue(OVERALL_ID)).thenReturn(Optional.of(overall));
            when(statsRepository.findByUser_IdAndCategory_IdAndActiveTrue(USER_ID, OVERALL_ID))
                    .thenReturn(Optional.empty());
            when(statsRepository.countActivePlayersInCategory(OVERALL_ID)).thenReturn(125000L);
            when(skillRepository.findByUserIdForOverall(USER_ID)).thenReturn(List.of());
            when(skillRepository.findByUserIdAndCategoryId(USER_ID, OVERALL_ID)).thenReturn(Optional.empty());
            when(skillRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            skillService.upsertSkill(USER_ID, OVERALL_ID);

            ArgumentCaptor<UserCategorySkill> captor = ArgumentCaptor.forClass(UserCategorySkill.class);
            verify(skillRepository).save(captor.capture());
            assertThat(captor.getValue().getSkillLevel().doubleValue()).isEqualTo(0);
        }

        @Test
        void noOpWhenUserMissing() {
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.empty());

            skillService.upsertSkill(USER_ID, CATEGORY_ID);

            verify(skillRepository, never()).save(any());
        }
    }

    @Nested
    class WeeklySnapshot {

        @Test
        void insertsWhenNoPriorSnapshotExists() {
            UserCategorySkill row = persistedSkill(category(CATEGORY_CODE, "True Acc"), 80.0, 75);
            when(skillRepository.findAll()).thenReturn(List.of(row));
            when(snapshotRepository.findFirstByUser_IdAndCategory_IdOrderByCapturedAtDesc(any(), any()))
                    .thenReturn(Optional.empty());

            skillService.captureWeeklySnapshots();

            verify(snapshotRepository).save(any(UserCategorySkillSnapshot.class));
        }

        @Test
        void skipsWhenUnchangedSinceLastSnapshot() {
            UserCategorySkill row = persistedSkill(category(CATEGORY_CODE, "True Acc"), 80.0, 75);
            UserCategorySkillSnapshot prior = UserCategorySkillSnapshot.builder()
                    .skillLevel(row.getSkillLevel())
                    .rankScore(row.getRankScore())
                    .sustainedScore(row.getSustainedScore())
                    .peakScore(row.getPeakScore())
                    .combinedScore(row.getCombinedScore())
                    .capturedAt(Instant.now())
                    .build();
            when(skillRepository.findAll()).thenReturn(List.of(row));
            when(snapshotRepository.findFirstByUser_IdAndCategory_IdOrderByCapturedAtDesc(any(), any()))
                    .thenReturn(Optional.of(prior));

            skillService.captureWeeklySnapshots();

            verify(snapshotRepository, never()).save(any(UserCategorySkillSnapshot.class));
        }

        @Test
        void insertsWhenAnyComponentChanged() {
            UserCategorySkill row = persistedSkill(category(CATEGORY_CODE, "True Acc"), 80.0, 75);
            UserCategorySkillSnapshot prior = UserCategorySkillSnapshot.builder()
                    .skillLevel(row.getSkillLevel())
                    .rankScore(row.getRankScore())
                    .sustainedScore(BigDecimal.valueOf(50))
                    .peakScore(row.getPeakScore())
                    .combinedScore(row.getCombinedScore())
                    .capturedAt(Instant.now())
                    .build();
            when(skillRepository.findAll()).thenReturn(List.of(row));
            when(snapshotRepository.findFirstByUser_IdAndCategory_IdOrderByCapturedAtDesc(any(), any()))
                    .thenReturn(Optional.of(prior));

            skillService.captureWeeklySnapshots();

            verify(snapshotRepository).save(any(UserCategorySkillSnapshot.class));
        }
    }

    private void mockUser() {
        when(userRepository.findByIdAndActiveTrue(USER_ID))
                .thenReturn(Optional.of(User.builder().id(USER_ID).name("Player").build()));
    }

    private void mockCategoryFiltered() {
        Category c = category(CATEGORY_CODE, "True Acc");
        when(categoryRepository.findByCodeAndActiveTrue(CATEGORY_CODE)).thenReturn(Optional.of(c));
    }

    private Category category(String code, String name) {
        return Category.builder()
                .id(code.equals(CATEGORY_CODE) ? CATEGORY_ID : UUID.randomUUID())
                .code(code).name(name)
                .weightCurve(Curve.builder().id(UUID.randomUUID()).build())
                .build();
    }

    private void mockStats(int rank, BigDecimal topAp) {
        Score topPlay = Score.builder().id(UUID.randomUUID()).ap(topAp).build();
        UserCategoryStatistics stats = UserCategoryStatistics.builder()
                .ranking(rank).topPlay(topPlay).build();
        when(statsRepository.findByUser_IdAndCategory_IdAndActiveTrue(USER_ID, CATEGORY_ID))
                .thenReturn(Optional.of(stats));
        lenient().when(scoreRepository.findActiveByUserAndCategoryOrderByApDesc(USER_ID, CATEGORY_ID))
                .thenReturn(List.of());
    }

    private void mockActivePlayers(long count) {
        when(statsRepository.countActivePlayersInCategory(CATEGORY_ID)).thenReturn(count);
    }

    private UserCategorySkill persistedSkill(Category category, double skill, double components) {
        return UserCategorySkill.builder()
                .user(User.builder().id(USER_ID).build())
                .category(category)
                .skillLevel(BigDecimal.valueOf(skill))
                .rankScore(BigDecimal.valueOf(components))
                .sustainedScore(BigDecimal.valueOf(components))
                .peakScore(BigDecimal.valueOf(components))
                .combinedScore(BigDecimal.valueOf(components))
                .topAp(BigDecimal.valueOf(1000))
                .categoryRank(50)
                .activePlayers(125000L)
                .build();
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
