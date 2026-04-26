package com.accsaber.backend.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class OverallStatisticsServiceTest {

        @Mock
        private UserCategoryStatisticsRepository statisticsRepository;
        @Mock
        private CategoryRepository categoryRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private RankingService rankingService;

        @InjectMocks
        private OverallStatisticsService overallStatisticsService;

        private User user;
        private Category overallCategory;
        private final Long userId = 76561198000000001L;

        @BeforeEach
        void setUp() {
                user = User.builder()
                                .id(userId)
                                .name("TestPlayer")
                                .country("US")
                                .active(true)
                                .build();

                overallCategory = Category.builder()
                                .id(UUID.randomUUID())
                                .code("overall")
                                .name("Overall")
                                .countForOverall(false)
                                .active(true)
                                .build();

                when(categoryRepository.findByCodeAndActiveTrue("overall")).thenReturn(Optional.of(overallCategory));
                lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        }

        private UserCategoryStatistics buildStat(BigDecimal ap, BigDecimal averageAcc, int rankedPlays) {
                return buildStat(ap, averageAcc, rankedPlays, BigDecimal.ZERO);
        }

        private UserCategoryStatistics buildStat(BigDecimal ap, BigDecimal averageAcc, int rankedPlays,
                        BigDecimal scoreXp) {
                Category cat = Category.builder()
                                .id(UUID.randomUUID())
                                .code("true_acc")
                                .countForOverall(true)
                                .active(true)
                                .build();
                return UserCategoryStatistics.builder()
                                .id(UUID.randomUUID())
                                .user(user)
                                .category(cat)
                                .ap(ap)
                                .scoreXp(scoreXp)
                                .averageAcc(averageAcc)
                                .rankedPlays(rankedPlays)
                                .active(true)
                                .build();
        }

        @Nested
        class Recalculate {

                @Test
                void apSummedAcrossMultipleCategories() {
                        UserCategoryStatistics s1 = buildStat(new BigDecimal("500.000000"), new BigDecimal("0.980000"),
                                        10);
                        UserCategoryStatistics s2 = buildStat(new BigDecimal("300.000000"), new BigDecimal("0.960000"),
                                        5);
                        when(statisticsRepository.findActiveByUserWhereCountForOverall(userId))
                                        .thenReturn(List.of(s1, s2));
                        when(statisticsRepository.findActiveForUpdate(userId,
                                        overallCategory.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        overallStatisticsService.recalculate(userId);

                        ArgumentCaptor<UserCategoryStatistics> captor = ArgumentCaptor
                                        .forClass(UserCategoryStatistics.class);
                        verify(statisticsRepository, times(1)).saveAndFlush(captor.capture());
                        assertThat(captor.getValue().getAp()).isEqualByComparingTo(new BigDecimal("800.000000"));
                }

                @Test
                void rankedPlaysSummedAcrossMultipleCategories() {
                        UserCategoryStatistics s1 = buildStat(new BigDecimal("500.000000"), new BigDecimal("0.980000"),
                                        10);
                        UserCategoryStatistics s2 = buildStat(new BigDecimal("300.000000"), new BigDecimal("0.960000"),
                                        5);
                        when(statisticsRepository.findActiveByUserWhereCountForOverall(userId))
                                        .thenReturn(List.of(s1, s2));
                        when(statisticsRepository.findActiveForUpdate(userId,
                                        overallCategory.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        overallStatisticsService.recalculate(userId);

                        ArgumentCaptor<UserCategoryStatistics> captor = ArgumentCaptor
                                        .forClass(UserCategoryStatistics.class);
                        verify(statisticsRepository).saveAndFlush(captor.capture());
                        assertThat(captor.getValue().getRankedPlays()).isEqualTo(15);
                }

                @Test
                void scoreXpSummedAcrossMultipleCategories() {
                        UserCategoryStatistics s1 = buildStat(new BigDecimal("500.000000"),
                                        new BigDecimal("0.980000"), 10, new BigDecimal("300.000000"));
                        UserCategoryStatistics s2 = buildStat(new BigDecimal("300.000000"),
                                        new BigDecimal("0.960000"), 5, new BigDecimal("150.000000"));
                        when(statisticsRepository.findActiveByUserWhereCountForOverall(userId))
                                        .thenReturn(List.of(s1, s2));
                        when(statisticsRepository.findActiveForUpdate(userId,
                                        overallCategory.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        overallStatisticsService.recalculate(userId);

                        ArgumentCaptor<UserCategoryStatistics> captor = ArgumentCaptor
                                        .forClass(UserCategoryStatistics.class);
                        verify(statisticsRepository, times(1)).saveAndFlush(captor.capture());
                        assertThat(captor.getValue().getScoreXp())
                                        .isEqualByComparingTo(new BigDecimal("450.000000"));
                }

                @Test
                void existingOverallStats_deactivatedAndNewVersionCreated() {
                        UserCategoryStatistics s1 = buildStat(new BigDecimal("500.000000"), new BigDecimal("0.980000"),
                                        10);
                        UserCategoryStatistics existing = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID())
                                        .user(user)
                                        .category(overallCategory)
                                        .ap(new BigDecimal("400.000000"))
                                        .rankedPlays(8)
                                        .active(true)
                                        .build();
                        when(statisticsRepository.findActiveByUserWhereCountForOverall(userId)).thenReturn(List.of(s1));
                        when(statisticsRepository.findActiveForUpdate(userId,
                                        overallCategory.getId()))
                                        .thenReturn(Optional.of(existing));
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        overallStatisticsService.recalculate(userId);

                        assertThat(existing.isActive()).isFalse();
                        ArgumentCaptor<UserCategoryStatistics> captor = ArgumentCaptor
                                        .forClass(UserCategoryStatistics.class);
                        verify(statisticsRepository, times(2)).saveAndFlush(captor.capture());
                        UserCategoryStatistics newStats = captor.getAllValues().stream()
                                        .filter(UserCategoryStatistics::isActive).findFirst().orElseThrow();
                        assertThat(newStats.getSupersedes()).isEqualTo(existing);
                }

                @Test
                void rankingServiceCalledWithOverallCategoryId() {
                        UserCategoryStatistics s1 = buildStat(new BigDecimal("500.000000"), new BigDecimal("0.980000"),
                                        10);
                        when(statisticsRepository.findActiveByUserWhereCountForOverall(userId)).thenReturn(List.of(s1));
                        when(statisticsRepository.findActiveForUpdate(userId,
                                        overallCategory.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        overallStatisticsService.recalculate(userId);

                        verify(rankingService).updateRankingForUserAsync(overallCategory.getId(), userId);
                }

                @Test
                void emptyStats_savesZeroApOverallRecord() {
                        when(statisticsRepository.findActiveByUserWhereCountForOverall(userId)).thenReturn(List.of());
                        when(statisticsRepository.findActiveForUpdate(userId,
                                        overallCategory.getId()))
                                        .thenReturn(Optional.empty());
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        overallStatisticsService.recalculate(userId);

                        ArgumentCaptor<UserCategoryStatistics> captor = ArgumentCaptor
                                        .forClass(UserCategoryStatistics.class);
                        verify(statisticsRepository, times(1)).saveAndFlush(captor.capture());
                        UserCategoryStatistics saved = captor.getValue();
                        assertThat(saved.getAp()).isEqualByComparingTo(BigDecimal.ZERO);
                        assertThat(saved.getRankedPlays()).isEqualTo(0);
                        assertThat(saved.isActive()).isTrue();
                }

                @Test
                void emptyStats_deactivatesExistingOverallIfPresent() {
                        UserCategoryStatistics existing = UserCategoryStatistics.builder()
                                        .id(UUID.randomUUID())
                                        .user(user)
                                        .category(overallCategory)
                                        .ap(new BigDecimal("400.000000"))
                                        .rankedPlays(5)
                                        .active(true)
                                        .build();
                        when(statisticsRepository.findActiveByUserWhereCountForOverall(userId)).thenReturn(List.of());
                        when(statisticsRepository.findActiveForUpdate(userId,
                                        overallCategory.getId()))
                                        .thenReturn(Optional.of(existing));
                        when(statisticsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

                        overallStatisticsService.recalculate(userId);

                        assertThat(existing.isActive()).isFalse();
                        verify(statisticsRepository, times(2)).saveAndFlush(any());
                }

                @Test
                void missingOverallCategory_throwsResourceNotFoundException() {
                        when(categoryRepository.findByCodeAndActiveTrue("overall")).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> overallStatisticsService.recalculate(userId))
                                        .isInstanceOf(ResourceNotFoundException.class);

                        verify(statisticsRepository, never()).saveAndFlush(any());
                }
        }
}
