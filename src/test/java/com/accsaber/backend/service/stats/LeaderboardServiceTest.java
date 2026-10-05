package com.accsaber.backend.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.user.UserCategoryRankingHistoryRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.repository.user.UserXpRankingHistoryRepository;
import com.accsaber.backend.service.milestone.LevelService;
import com.accsaber.backend.service.supporter.SupporterService;

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
        private UserCategoryRankingHistoryRepository categoryRankingHistoryRepository;
        @Mock
        private LevelService levelService;
        @Mock
        private SupporterService supporterService;

        private LeaderboardService leaderboardService;

        private Category category;

        @BeforeEach
        void setUp() {
                leaderboardService = new LeaderboardService(statisticsRepository, categoryRepository,
                                userRepository, xpRankingHistoryRepository, categoryRankingHistoryRepository,
                                levelService, supporterService);

                org.mockito.Mockito.lenient().when(categoryRankingHistoryRepository.findRankingsOneWeekAgo(any(), any()))
                                .thenReturn(List.of());
                org.mockito.Mockito.lenient().when(supporterService.findCurrentTiersByUserIds(any()))
                                .thenReturn(java.util.Map.of());

                category = Category.builder()
                                .id(UUID.randomUUID())
                                .code("true_acc")
                                .name("True Acc")
                                .active(true)
                                .build();
        }

        @Nested
        class GetBoardWithoutCountry {

                @Test
                void defaultsToGlobalRankingSort() {
                        PageRequest pageable = PageRequest.of(0, 20);
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryPaged(eq(category.getId()), eq(true), any(), any()))
                                        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

                        leaderboardService.getBoard(category.getId(), null, null, null, true, pageable);

                        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
                        verify(statisticsRepository).findActiveByCategoryPaged(eq(category.getId()), eq(true), any(),
                                        captor.capture());
                        assertThat(captor.getValue().getSort().getOrderFor("ranking")).isNotNull();
                }

                @Test
                void unknownCategoryThrows() {
                        UUID unknownId = UUID.randomUUID();
                        when(categoryRepository.findByIdAndActiveTrue(unknownId)).thenReturn(Optional.empty());

                        assertThatThrownBy(
                                        () -> leaderboardService.getBoard(unknownId, null, null, null, true,
                                                        PageRequest.of(0, 20)))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class GetBoardWithCountry {

                @Test
                void defaultsToCountryRankingSort() {
                        PageRequest pageable = PageRequest.of(0, 20);
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryAndCountryPaged(
                                        eq(category.getId()), eq("US"), eq(true), any(), any()))
                                        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

                        leaderboardService.getBoard(category.getId(), "US", null, null, true, pageable);

                        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
                        verify(statisticsRepository).findActiveByCategoryAndCountryPaged(eq(category.getId()), eq("US"),
                                        eq(true), any(), captor.capture());
                        assertThat(captor.getValue().getSort().getOrderFor("countryRanking")).isNotNull();
                }

                @Test
                void blankCountryFallsBackToGlobalBoard() {
                        PageRequest pageable = PageRequest.of(0, 20);
                        when(categoryRepository.findByIdAndActiveTrue(category.getId()))
                                        .thenReturn(Optional.of(category));
                        when(statisticsRepository.findActiveByCategoryPaged(eq(category.getId()), eq(true), any(), any()))
                                        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

                        leaderboardService.getBoard(category.getId(), "  ", null, null, true, pageable);

                        verify(statisticsRepository).findActiveByCategoryPaged(eq(category.getId()), eq(true), any(),
                                        any());
                }
        }
}
