package com.accsaber.backend.service.stats;

import com.accsaber.backend.util.Rounding;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class OverallStatisticsService {

        private static final int SCALE = 6;

        private final UserCategoryStatisticsRepository statisticsRepository;
        private final CategoryRepository categoryRepository;
        private final UserRepository userRepository;
        private final RankingService rankingService;

        public void recalculate(Long userId) {
                recalculate(userId, true);
        }

        public void recalculate(Long userId, boolean triggerRanking) {
                Category overallCategory = categoryRepository.requireActiveByCode("overall");
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

                UserCategoryStatistics current = statisticsRepository
                                .findActiveForUpdate(userId, overallCategory.getId())
                                .orElse(null);

                List<UserCategoryStatistics> sourceStats = statisticsRepository
                                .findActiveByUserWhereCountForOverall(userId);

                Double totalAp = sumAp(sourceStats);
                Double totalScoreXp = sumScoreXp(sourceStats);
                int totalPlays = sumRankedPlays(sourceStats);
                Double avgAcc = computeWeightedAverageAcc(sourceStats, totalPlays);
                Double avgAp = totalPlays == 0
                                ? 0.0
                                : Rounding.round(totalAp / (double) (totalPlays), SCALE);
                Score topPlay = sourceStats.stream()
                                .map(UserCategoryStatistics::getTopPlay)
                                .filter(Objects::nonNull)
                                .max(Comparator.comparingDouble(Score::getAp))
                                .orElse(null);

                if (current != null) {
                        current.setActive(false);
                        statisticsRepository.saveAndFlush(current);
                }

                UserCategoryStatistics newStats = UserCategoryStatistics.builder()
                                .user(user)
                                .category(overallCategory)
                                .ap(totalAp)
                                .scoreXp(totalScoreXp)
                                .averageAcc(avgAcc)
                                .averageAp(avgAp)
                                .ranking(current != null ? current.getRanking() : null)
                                .countryRanking(current != null ? current.getCountryRanking() : null)
                                .rankedPlays(totalPlays)
                                .topPlay(topPlay)
                                .supersedes(current)
                                .supersedesReason("Score submission")
                                .supersedesAuthor(userId)
                                .active(true)
                                .build();
                statisticsRepository.saveAndFlush(newStats);

                if (triggerRanking) {
                        rankingService.updateRankingForUserAsync(overallCategory.getId(), userId);
                }
        }

        public void updateOverallRankings() {
                categoryRepository.findByCodeAndActiveTrue("overall")
                                .ifPresent(c -> rankingService.updateRankings(c.getId()));
        }

        private Double sumAp(List<UserCategoryStatistics> stats) {
                return Rounding.round(stats.stream()
                                .mapToDouble(UserCategoryStatistics::getAp)
                                .sum(), SCALE);
        }

        private int sumRankedPlays(List<UserCategoryStatistics> stats) {
                return stats.stream()
                                .mapToInt(UserCategoryStatistics::getRankedPlays)
                                .sum();
        }

        private Double sumScoreXp(List<UserCategoryStatistics> stats) {
                return Rounding.round(stats.stream()
                                .mapToDouble(UserCategoryStatistics::getScoreXp)
                                .sum(), SCALE);
        }

        private Double computeWeightedAverageAcc(List<UserCategoryStatistics> stats, int totalPlays) {
                if (totalPlays == 0)
                        return null;
                List<UserCategoryStatistics> withAcc = stats.stream()
                                .filter(s -> s.getAverageAcc() != null && s.getRankedPlays() > 0)
                                .toList();
                if (withAcc.isEmpty())
                        return null;
                double weightedSum = withAcc.stream()
                                .mapToDouble(s -> s.getAverageAcc() * s.getRankedPlays())
                                .sum();
                return Rounding.round(weightedSum / totalPlays, SCALE);
        }
}
