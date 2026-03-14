package com.accsaber.backend.service.stats;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
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

        private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
        private static final int SCALE = 6;

        private final UserCategoryStatisticsRepository statisticsRepository;
        private final CategoryRepository categoryRepository;
        private final UserRepository userRepository;
        private final RankingService rankingService;

        public void recalculate(Long userId) {
                recalculate(userId, true);
        }

        public void recalculate(Long userId, boolean triggerRanking) {
                Category overallCategory = categoryRepository.findByCodeAndActiveTrue("overall")
                                .orElseThrow(() -> new ResourceNotFoundException("Category", "overall"));
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

                List<UserCategoryStatistics> sourceStats = statisticsRepository
                                .findActiveByUserWhereCountForOverall(userId);

                BigDecimal totalAp = sumAp(sourceStats);
                int totalPlays = sumRankedPlays(sourceStats);
                BigDecimal avgAcc = computeWeightedAverageAcc(sourceStats, totalPlays);
                BigDecimal avgAp = totalPlays == 0
                                ? BigDecimal.ZERO
                                : totalAp.divide(BigDecimal.valueOf(totalPlays), SCALE, RoundingMode.HALF_UP);
                Score topPlay = sourceStats.stream()
                                .map(UserCategoryStatistics::getTopPlay)
                                .filter(Objects::nonNull)
                                .filter(s -> s.getAp() != null)
                                .max(Comparator.comparing(Score::getAp))
                                .orElse(null);

                UserCategoryStatistics current = statisticsRepository
                                .findByUser_IdAndCategory_IdAndActiveTrue(userId, overallCategory.getId())
                                .orElse(null);

                if (current != null) {
                        current.setActive(false);
                        statisticsRepository.saveAndFlush(current);
                }

                UserCategoryStatistics newStats = UserCategoryStatistics.builder()
                                .user(user)
                                .category(overallCategory)
                                .ap(totalAp)
                                .averageAcc(avgAcc)
                                .averageAp(avgAp)
                                .rankedPlays(totalPlays)
                                .topPlay(topPlay)
                                .supersedes(current)
                                .supersedesReason("Score submission")
                                .supersedesAuthor(userId)
                                .active(true)
                                .build();
                statisticsRepository.saveAndFlush(newStats);

                if (triggerRanking) {
                        rankingService.updateRankings(overallCategory.getId());
                }
        }

        public void updateOverallRankings() {
                categoryRepository.findByCodeAndActiveTrue("overall")
                                .ifPresent(c -> rankingService.updateRankings(c.getId()));
        }

        private BigDecimal sumAp(List<UserCategoryStatistics> stats) {
                return stats.stream()
                                .map(UserCategoryStatistics::getAp)
                                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC))
                                .setScale(SCALE, RoundingMode.HALF_UP);
        }

        private int sumRankedPlays(List<UserCategoryStatistics> stats) {
                return stats.stream()
                                .mapToInt(UserCategoryStatistics::getRankedPlays)
                                .sum();
        }

        private BigDecimal computeWeightedAverageAcc(List<UserCategoryStatistics> stats, int totalPlays) {
                if (totalPlays == 0)
                        return null;
                List<UserCategoryStatistics> withAcc = stats.stream()
                                .filter(s -> s.getAverageAcc() != null && s.getRankedPlays() > 0)
                                .toList();
                if (withAcc.isEmpty())
                        return null;
                BigDecimal weightedSum = withAcc.stream()
                                .map(s -> s.getAverageAcc().multiply(BigDecimal.valueOf(s.getRankedPlays()), MC))
                                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
                return weightedSum.divide(BigDecimal.valueOf(totalPlays), SCALE, RoundingMode.HALF_UP);
        }
}
