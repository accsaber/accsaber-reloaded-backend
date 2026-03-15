package com.accsaber.backend.service.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.function.Consumer;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.service.infra.CacheService;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

        @Mock
        private UserCategoryStatisticsRepository statisticsRepository;

        @Mock
        private CacheService cacheService;

        @Mock
        private TransactionTemplate transactionTemplate;

        private RankingService rankingService;

        @BeforeEach
        void setUp() {
                doAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Consumer<TransactionStatus> action = invocation.getArgument(0, Consumer.class);
                        action.accept(null);
                        return null;
                }).when(transactionTemplate).executeWithoutResult(any());
                rankingService = new RankingService(statisticsRepository, cacheService, transactionTemplate);
        }

        @Nested
        class UpdateRankings {

                @Test
                void assignsGlobalAndCountryRankings() {
                        UUID categoryId = UUID.randomUUID();

                        rankingService.updateRankings(categoryId);

                        verify(statisticsRepository).assignGlobalRankings(categoryId);
                        verify(statisticsRepository).assignCountryRankings(categoryId);
                }

                @Test
                void evictsLeaderboardCache() {
                        UUID categoryId = UUID.randomUUID();

                        rankingService.updateRankings(categoryId);

                        verify(cacheService).evictLeaderboard(categoryId);
                }
        }
}
