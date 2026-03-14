package com.accsaber.backend.service.stats;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.service.infra.CacheService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final UserCategoryStatisticsRepository statisticsRepository;
    private final CacheService cacheService;

    @Async("rankingExecutor")
    @Transactional
    public void updateRankings(UUID categoryId) {
        List<UserCategoryStatistics> stats = statisticsRepository.findActiveByCategoryOrderByApDesc(categoryId);
        if (stats.isEmpty())
            return;

        assignGlobalRankings(stats);
        assignCountryRankings(stats);

        statisticsRepository.saveAll(stats);
        cacheService.evictLeaderboard(categoryId);
    }

    private void assignGlobalRankings(List<UserCategoryStatistics> stats) {
        for (int i = 0; i < stats.size(); i++) {
            stats.get(i).setRanking(i + 1);
        }
    }

    private void assignCountryRankings(List<UserCategoryStatistics> stats) {
        Map<String, Integer> countryCounters = new HashMap<>();
        List<UserCategoryStatistics> sortedByCountry = stats.stream()
                .filter(s -> s.getUser() != null && s.getUser().getCountry() != null)
                .sorted(Comparator.comparingInt(UserCategoryStatistics::getRanking))
                .toList();

        for (UserCategoryStatistics stat : sortedByCountry) {
            String country = stat.getUser().getCountry();
            int next = countryCounters.getOrDefault(country, 0) + 1;
            stat.setCountryRanking(next);
            countryCounters.put(country, next);
        }
    }
}
