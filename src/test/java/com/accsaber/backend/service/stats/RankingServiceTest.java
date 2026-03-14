package com.accsaber.backend.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.service.infra.CacheService;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

        @Mock
        private UserCategoryStatisticsRepository statisticsRepository;

        @Mock
        private CacheService cacheService;

        @InjectMocks
        private RankingService rankingService;

        private Category category;

        @BeforeEach
        void setUp() {
                category = Category.builder()
                                .id(UUID.randomUUID())
                                .code("true_acc")
                                .name("True Acc")
                                .active(true)
                                .build();
        }

        private UserCategoryStatistics buildStats(BigDecimal ap, String country) {
                User user = User.builder()
                                .id(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)
                                .name("Player")
                                .country(country)
                                .active(true)
                                .build();
                return UserCategoryStatistics.builder()
                                .id(UUID.randomUUID())
                                .user(user)
                                .category(category)
                                .ap(ap)
                                .rankedPlays(1)
                                .active(true)
                                .build();
        }

        @Nested
        class UpdateRankings {

                @Test
                void assignsSequentialGlobalRankings() {
                        UserCategoryStatistics s1 = buildStats(new BigDecimal("300"), "US");
                        UserCategoryStatistics s2 = buildStats(new BigDecimal("200"), "US");
                        UserCategoryStatistics s3 = buildStats(new BigDecimal("100"), "US");
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of(s1, s2, s3));
                        when(statisticsRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

                        rankingService.updateRankings(category.getId());

                        assertThat(s1.getRanking()).isEqualTo(1);
                        assertThat(s2.getRanking()).isEqualTo(2);
                        assertThat(s3.getRanking()).isEqualTo(3);
                }

                @Test
                void assignsCountryRankingsPerCountry() {
                        UserCategoryStatistics usA = buildStats(new BigDecimal("400"), "US");
                        UserCategoryStatistics frA = buildStats(new BigDecimal("300"), "FR");
                        UserCategoryStatistics usB = buildStats(new BigDecimal("200"), "US");
                        UserCategoryStatistics frB = buildStats(new BigDecimal("100"), "FR");
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of(usA, frA, usB, frB));
                        when(statisticsRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

                        rankingService.updateRankings(category.getId());

                        assertThat(usA.getCountryRanking()).isEqualTo(1);
                        assertThat(usB.getCountryRanking()).isEqualTo(2);
                        assertThat(frA.getCountryRanking()).isEqualTo(1);
                        assertThat(frB.getCountryRanking()).isEqualTo(2);
                }

                @Test
                void emptyCategory_noSaveCall() {
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of());

                        rankingService.updateRankings(category.getId());

                        verify(statisticsRepository, never()).saveAll(any());
                }

                @Test
                void updatingRankings_evictsLeaderboardCache() {
                        UserCategoryStatistics s1 = buildStats(new BigDecimal("300"), "US");
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of(s1));
                        when(statisticsRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

                        rankingService.updateRankings(category.getId());

                        verify(cacheService).evictLeaderboard(category.getId());
                }

                @Test
                void playerWithNullCountry_getsNoCountryRanking() {
                        UserCategoryStatistics withCountry = buildStats(new BigDecimal("300"), "US");
                        UserCategoryStatistics noCountry = buildStats(new BigDecimal("200"), null);
                        when(statisticsRepository.findActiveByCategoryOrderByApDesc(category.getId()))
                                        .thenReturn(List.of(withCountry, noCountry));
                        when(statisticsRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

                        rankingService.updateRankings(category.getId());

                        assertThat(withCountry.getCountryRanking()).isEqualTo(1);
                        assertThat(noCountry.getCountryRanking()).isNull();
                }
        }
}
