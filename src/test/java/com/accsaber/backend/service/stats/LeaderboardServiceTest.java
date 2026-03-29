package com.accsaber.backend.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.LeaderboardResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.repository.user.UserXpRankingHistoryRepository;
import com.accsaber.backend.service.milestone.LevelService;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

        @Mock
        private UserCategoryStatisticsRepository statisticsRepository;
        @Mock
        private CategoryRepository categoryRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private UserXpRankingHistoryRepository xpRankingHistoryRepository;
        @Mock
        private LevelService levelService;

        private LeaderboardService leaderboardService;

        private Category category;

        @BeforeEach
        void setUp() {
                leaderboardService = new LeaderboardService(statisticsRepository, categoryRepository,
                                userRepository, xpRankingHistoryRepository, levelService);

                org.mockito.Mockito.lenient().when(statisticsRepository.findRankingsOneWeekAgo(any(), any()))
                                .thenReturn(List.of());

                category = Category.builder()
                                .id(UUID.randomUUID())
                                .code("true_acc")
                                .name("True Acc")
                                .active(true)
                                .build();
        }

        private UserCategoryStatistics buildStats(BigDecimal ap, String country, int ranking, int countryRanking) {
                User user = User.builder()
                                .id(Math.abs(UUID.randomUUID().getMostSignificantBits()))
                                .name("Player")
                                .country(country)
                                .active(true)
                                .build();
                return UserCategoryStatistics.builder()
                                .id(UUID.randomUUID())
                                .user(user)
                                .category(category)
                                .ap(ap)
                                .rankedPlays(5)
                                .ranking(ranking)
                                .countryRanking(countryRanking)
                                .active(true)
                                .build();
        }

        @Nested
        class GetGlobal {

                @Test
                void returnsPagedResults() {
                        PageRequest pageable = PageRequest.of(0, 2);
                        UserCategoryStatistics s1 = buildStats(new BigDecimal("300"), "US", 1, 1);
                        UserCategoryStatistics s2 = buildStats(new BigDecimal("200"), "FR", 2, 1);
                        Page<UserCategoryStatistics> page = new PageImpl<>(List.of(s1, s2), pageable, 3);

                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryPaged(eq(category.getId()), eq(true), any(), any()))
                                        .thenReturn(page);

                        Page<LeaderboardResponse> result = leaderboardService.getGlobal(category.getId(), null,
                                        null, true, pageable);

                        assertThat(result.getTotalElements()).isEqualTo(3);
                        assertThat(result.getContent()).hasSize(2);
                        assertThat(result.getContent().get(0).getRanking()).isEqualTo(1);
                }

                @Test
                void unknownCategoryThrows() {
                        UUID unknownId = UUID.randomUUID();
                        when(categoryRepository.findByIdAndActiveTrue(unknownId)).thenReturn(Optional.empty());

                        assertThatThrownBy(
                                        () -> leaderboardService.getGlobal(unknownId, null, null, true, PageRequest.of(0, 20)))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class GetByCountry {

                @Test
                void returnsFilteredPagedResults() {
                        PageRequest pageable = PageRequest.of(0, 20);
                        UserCategoryStatistics us1 = buildStats(new BigDecimal("300"), "US", 1, 1);
                        UserCategoryStatistics us2 = buildStats(new BigDecimal("100"), "US", 3, 2);
                        Page<UserCategoryStatistics> page = new PageImpl<>(List.of(us1, us2), pageable, 2);

                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryAndCountryPaged(
                                        eq(category.getId()), eq("US"), eq(true), any(), any()))
                                        .thenReturn(page);

                        Page<LeaderboardResponse> result = leaderboardService.getByCountry(
                                        category.getId(), "US", null, null, true, pageable);

                        assertThat(result.getTotalElements()).isEqualTo(2);
                        assertThat(result.getContent()).allMatch(r -> "US".equals(r.getCountry()));
                }

                @Test
                void unknownCategoryThrows() {
                        UUID unknownId = UUID.randomUUID();
                        when(categoryRepository.findByIdAndActiveTrue(unknownId)).thenReturn(Optional.empty());

                        assertThatThrownBy(
                                        () -> leaderboardService.getByCountry(unknownId, "US", null, null, true,
                                                        PageRequest.of(0, 20)))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }
}
