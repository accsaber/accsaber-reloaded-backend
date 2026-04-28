package com.accsaber.backend.service.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import com.accsaber.backend.config.SkillProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.SkillCategoryResponse;
import com.accsaber.backend.model.dto.response.player.SkillResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.score.APCalculationService;
import com.github.benmanes.caffeine.cache.Caffeine;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    private static final Long USER_ID = 76561198000000123L;
    private static final UUID CATEGORY_ID = UUID.randomUUID();
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
    private APCalculationService apCalculationService;
    @Mock
    private CacheManager cacheManager;

    private SkillProperties skillProperties;
    private SkillService skillService;

    @BeforeEach
    void setUp() {
        skillProperties = new SkillProperties();
        skillService = new SkillService(userRepository, categoryRepository, statsRepository,
                scoreRepository, apCalculationService, skillProperties, cacheManager);
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
        void sigmoidScoreApproachesZeroAtLowRaw() {
            assertThat(skillService.sigmoidScore(0, 750, 90)).isLessThan(1.0);
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
        void harmonicMeanRewardsConsistency() {
            assertThat(skillService.harmonicMean(95, 95)).isCloseTo(95.0, within(0.1));
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
    }

    @Nested
    class ComputeSkill {

        @Test
        void godTierApproachesMax() {
            mockUser();
            mockCategoryFiltered();
            mockStats(2, BigDecimal.valueOf(1100));
            mockActivePlayers(125000);
            mockRawApForOneGain(BigDecimal.valueOf(1070));

            SkillResponse response = skillService.computeSkillForUser(USER_ID, CATEGORY_CODE);

            SkillCategoryResponse cat = response.getSkills().get(0);
            assertThat(cat.getSkillLevel()).isGreaterThan(90.0);
        }

        @Test
        void onePlayPlayerCraters() {
            mockUser();
            mockCategoryFiltered();
            mockStats(120000, BigDecimal.valueOf(600));
            mockActivePlayers(125000);
            mockRawApForOneGain(BigDecimal.valueOf(2));

            SkillResponse response = skillService.computeSkillForUser(USER_ID, CATEGORY_CODE);

            assertThat(response.getSkills().get(0).getSkillLevel()).isLessThan(15.0);
        }

        @Test
        void tenPlaysAtTop100GetsHighSkillFromRank() {
            mockUser();
            mockCategoryFiltered();
            mockStats(100, BigDecimal.valueOf(1100));
            mockActivePlayers(125000);
            mockRawApForOneGain(BigDecimal.valueOf(50));

            SkillResponse response = skillService.computeSkillForUser(USER_ID, CATEGORY_CODE);

            double skill = response.getSkills().get(0).getSkillLevel();
            assertThat(skill).isGreaterThan(50.0).isLessThan(75.0);
        }

        @Test
        void rankOneWithHigherPeakBeatsRankTwoEvenWhenSustainedIsLower() {
            mockUser();
            mockCategoryFiltered();
            mockActivePlayers(125000);

            mockStats(1, BigDecimal.valueOf(1150));
            mockRawApForOneGain(BigDecimal.valueOf(1060));
            double rank1Skill = skillService.computeSkillForUser(USER_ID, CATEGORY_CODE)
                    .getSkills().get(0).getSkillLevel();

            mockStats(2, BigDecimal.valueOf(1080));
            mockRawApForOneGain(BigDecimal.valueOf(1070));
            double rank2Skill = skillService.computeSkillForUser(USER_ID, CATEGORY_CODE)
                    .getSkills().get(0).getSkillLevel();

            assertThat(rank1Skill).isGreaterThan(rank2Skill);
        }

        @Test
        void noStatsProducesZeroSkill() {
            mockUser();
            mockCategoryFiltered();
            when(statsRepository.findByUser_IdAndCategory_IdAndActiveTrue(USER_ID, CATEGORY_ID))
                    .thenReturn(Optional.empty());
            mockActivePlayers(125000);
            mockRawApForOneGain(BigDecimal.ZERO);

            SkillResponse response = skillService.computeSkillForUser(USER_ID, CATEGORY_CODE);

            SkillCategoryResponse cat = response.getSkills().get(0);
            assertThat(cat.getSkillLevel()).isLessThan(5.0);
            assertThat(cat.getComponents().getCategoryRank()).isNull();
        }

        @Test
        void categoryWithoutWeightCurveFallsBackToPeak() {
            mockUser();
            Category overall = Category.builder().id(CATEGORY_ID).code("overall").name("Overall").build();
            when(categoryRepository.findByCodeAndActiveTrue("overall")).thenReturn(Optional.of(overall));
            mockStats(2, BigDecimal.valueOf(1100));
            mockActivePlayers(125000);

            SkillResponse response = skillService.computeSkillForUser(USER_ID, "overall");
            SkillCategoryResponse cat = response.getSkills().get(0);

            assertThat(cat.getComponents().getSustained()).isEqualTo(0);
            assertThat(cat.getComponents().getRawApForOneGain()).isNull();
            assertThat(cat.getComponents().getCombined()).isCloseTo(cat.getComponents().getPeak(), within(0.1));
            assertThat(cat.getSkillLevel()).isGreaterThan(90.0);
        }

        @Test
        void exposesAllComponents() {
            mockUser();
            mockCategoryFiltered();
            mockStats(300, BigDecimal.valueOf(1000));
            mockActivePlayers(125000);
            mockRawApForOneGain(BigDecimal.valueOf(800));

            SkillResponse response = skillService.computeSkillForUser(USER_ID, CATEGORY_CODE);
            SkillCategoryResponse.SkillComponents comp = response.getSkills().get(0).getComponents();

            assertThat(comp.getRank()).isGreaterThan(0);
            assertThat(comp.getSustained()).isGreaterThan(0);
            assertThat(comp.getPeak()).isGreaterThan(0);
            assertThat(comp.getCombined()).isGreaterThan(0);
            assertThat(comp.getRawApForOneGain()).isEqualByComparingTo("800");
            assertThat(comp.getTopAp()).isEqualByComparingTo("1000");
            assertThat(comp.getCategoryRank()).isEqualTo(300);
            assertThat(comp.getActivePlayers()).isEqualTo(125000);
        }
    }

    @Nested
    class CategoryFiltering {

        @Test
        void omittedCategoryReturnsAll() {
            mockUser();
            Category catA = category("true_acc", "True Acc");
            Category catB = category("standard_acc", "Standard Acc");
            when(categoryRepository.findByActiveTrue()).thenReturn(List.of(catA, catB));
            lenient().when(statsRepository.findByUser_IdAndCategory_IdAndActiveTrue(eq(USER_ID), any()))
                    .thenReturn(Optional.empty());
            lenient().when(statsRepository.countActivePlayersInCategory(any())).thenReturn(125000L);
            lenient().when(apCalculationService.calculateRawApForOneWeightedGain(any(), any()))
                    .thenReturn(BigDecimal.ZERO);

            SkillResponse response = skillService.computeSkillForUser(USER_ID, null);

            assertThat(response.getSkills()).hasSize(2);
            assertThat(response.getSkills()).extracting(SkillCategoryResponse::getCategoryCode)
                    .containsExactly("true_acc", "standard_acc");
        }

        @Test
        void unknownCategoryThrows() {
            mockUser();
            when(categoryRepository.findByCodeAndActiveTrue("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> skillService.computeSkillForUser(USER_ID, "nope"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void unknownUserThrows() {
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> skillService.computeSkillForUser(USER_ID, CATEGORY_CODE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class CacheEviction {

        @Test
        void evictForUserRemovesOnlyThatUsersEntries() {
            CaffeineCache cache = new CaffeineCache("skill", Caffeine.newBuilder().build());
            cache.put(USER_ID + ":all", "user-cached");
            cache.put(USER_ID + ":true_acc", "user-true-acc-cached");
            cache.put(99999L + ":all", "other-user-cached");
            when(cacheManager.getCache("skill")).thenReturn(cache);

            skillService.evictForUser(USER_ID);

            assertThat(cache.get(USER_ID + ":all")).isNull();
            assertThat(cache.get(USER_ID + ":true_acc")).isNull();
            assertThat(cache.get(99999L + ":all")).isNotNull();
        }

        @Test
        void evictForUserNoOpWhenCacheMissing() {
            when(cacheManager.getCache("skill")).thenReturn(null);
            skillService.evictForUser(USER_ID);
        }

        @Test
        void onScoreSubmittedEvictsSubmittingUser() {
            CaffeineCache cache = new CaffeineCache("skill", Caffeine.newBuilder().build());
            cache.put(USER_ID + ":all", "cached");
            when(cacheManager.getCache("skill")).thenReturn(cache);
            ScoreResponse score = ScoreResponse.builder().userId(String.valueOf(USER_ID)).build();

            skillService.onScoreSubmitted(new ScoreSubmittedEvent(score));

            assertThat(cache.get(USER_ID + ":all")).isNull();
        }

        @Test
        void onScoreSubmittedSwallowsNonNumericUserId() {
            ScoreResponse score = ScoreResponse.builder().userId("not-a-number").build();
            skillService.onScoreSubmitted(new ScoreSubmittedEvent(score));
        }
    }

    @Nested
    class ApToNext {

        @Test
        void delegatesToCalculator() {
            mockUser();
            mockCategoryFiltered();
            mockRawApForOneGain(BigDecimal.valueOf(812.5));
            when(scoreRepository.findActiveByUserAndCategoryOrderByApDesc(USER_ID, CATEGORY_ID))
                    .thenReturn(List.of());

            var response = skillService.calculateApToNext(USER_ID, CATEGORY_CODE);

            assertThat(response.getCategoryCode()).isEqualTo(CATEGORY_CODE);
            assertThat(response.getRawApForOneGain()).isEqualByComparingTo("812.5");
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
        return Category.builder().id(code.equals(CATEGORY_CODE) ? CATEGORY_ID : UUID.randomUUID())
                .code(code).name(name)
                .weightCurve(Curve.builder().id(UUID.randomUUID()).build())
                .build();
    }

    private void mockStats(int rank, BigDecimal topAp) {
        Score topPlay = Score.builder().id(UUID.randomUUID()).ap(topAp).build();
        UserCategoryStatistics stats = UserCategoryStatistics.builder()
                .ranking(rank)
                .topPlay(topPlay)
                .build();
        when(statsRepository.findByUser_IdAndCategory_IdAndActiveTrue(USER_ID, CATEGORY_ID))
                .thenReturn(Optional.of(stats));
        lenient().when(scoreRepository.findActiveByUserAndCategoryOrderByApDesc(USER_ID, CATEGORY_ID))
                .thenReturn(List.of());
    }

    private void mockActivePlayers(long count) {
        when(statsRepository.countActivePlayersInCategory(CATEGORY_ID)).thenReturn(count);
    }

    private void mockRawApForOneGain(BigDecimal value) {
        when(apCalculationService.calculateRawApForOneWeightedGain(any(), any())).thenReturn(value);
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
