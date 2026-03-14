package com.accsaber.backend.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.LeaderboardResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

        @Mock
        private UserCategoryStatisticsRepository statisticsRepository;
        @Mock
        private CategoryRepository categoryRepository;

        private LeaderboardService leaderboardService;

        private Category category;

        @BeforeEach
        void setUp() {
                leaderboardService = new LeaderboardService(statisticsRepository, categoryRepository);
                ReflectionTestUtils.setField(leaderboardService, "self", leaderboardService);

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
                void paginatesResultsCorrectly() {
                        UserCategoryStatistics s1 = buildStats(new BigDecimal("300"), "US", 1, 1);
                        UserCategoryStatistics s2 = buildStats(new BigDecimal("200"), "FR", 2, 1);
                        UserCategoryStatistics s3 = buildStats(new BigDecimal("100"), "US", 3, 2);
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of(s1, s2, s3));

                        Page<LeaderboardResponse> page = leaderboardService.getGlobal(category.getId(),
                                        PageRequest.of(0, 2));

                        assertThat(page.getTotalElements()).isEqualTo(3);
                        assertThat(page.getContent()).hasSize(2);
                        assertThat(page.getContent().get(0).getRanking()).isEqualTo(1);
                }

                @Test
                void pageOutOfRange_returnsEmptyPage() {
                        UserCategoryStatistics s1 = buildStats(new BigDecimal("300"), "US", 1, 1);
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of(s1));

                        Page<LeaderboardResponse> page = leaderboardService.getGlobal(category.getId(),
                                        PageRequest.of(5, 20));

                        assertThat(page.getContent()).isEmpty();
                        assertThat(page.getTotalElements()).isEqualTo(1);
                }

                @Test
                void emptyCategoryReturnsEmptyPage() {
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of());

                        Page<LeaderboardResponse> page = leaderboardService.getGlobal(category.getId(),
                                        PageRequest.of(0, 20));

                        assertThat(page.getContent()).isEmpty();
                        assertThat(page.getTotalElements()).isZero();
                }

                @Test
                void unknownCategoryThrows() {
                        UUID unknownId = UUID.randomUUID();
                        when(categoryRepository.findByIdAndActiveTrue(unknownId)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> leaderboardService.getGlobal(unknownId, PageRequest.of(0, 20)))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class GetByCountry {

                @Test
                void filtersToMatchingCountryOnly() {
                        UserCategoryStatistics us1 = buildStats(new BigDecimal("300"), "US", 1, 1);
                        UserCategoryStatistics fr1 = buildStats(new BigDecimal("200"), "FR", 2, 1);
                        UserCategoryStatistics us2 = buildStats(new BigDecimal("100"), "US", 3, 2);
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of(us1, fr1, us2));

                        Page<LeaderboardResponse> page = leaderboardService.getByCountry(
                                        category.getId(), "US", PageRequest.of(0, 20));

                        assertThat(page.getTotalElements()).isEqualTo(2);
                        assertThat(page.getContent()).allMatch(r -> "US".equals(r.getCountry()));
                }

                @Test
                void countryFilterIsCaseInsensitive() {
                        UserCategoryStatistics us = buildStats(new BigDecimal("300"), "US", 1, 1);
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of(us));

                        Page<LeaderboardResponse> lower = leaderboardService.getByCountry(
                                        category.getId(), "us", PageRequest.of(0, 20));
                        Page<LeaderboardResponse> upper = leaderboardService.getByCountry(
                                        category.getId(), "US", PageRequest.of(0, 20));

                        assertThat(lower.getTotalElements()).isEqualTo(1);
                        assertThat(upper.getTotalElements()).isEqualTo(1);
                }

                @Test
                void countryRankingValuesArePreservedFromEntity() {
                        UserCategoryStatistics us1 = buildStats(new BigDecimal("300"), "US", 1, 1);
                        UserCategoryStatistics us2 = buildStats(new BigDecimal("200"), "US", 3, 2);
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of(us1, us2));

                        Page<LeaderboardResponse> page = leaderboardService.getByCountry(
                                        category.getId(), "US", PageRequest.of(0, 20));

                        assertThat(page.getContent().get(0).getCountryRanking()).isEqualTo(1);
                        assertThat(page.getContent().get(1).getCountryRanking()).isEqualTo(2);
                }

                @Test
                void unknownCategoryThrows() {
                        UUID unknownId = UUID.randomUUID();
                        when(categoryRepository.findByIdAndActiveTrue(unknownId)).thenReturn(Optional.empty());

                        assertThatThrownBy(
                                        () -> leaderboardService.getByCountry(unknownId, "US", PageRequest.of(0, 20)))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }
}
