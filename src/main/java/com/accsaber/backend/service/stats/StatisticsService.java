package com.accsaber.backend.service.stats;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.UserCategoryStatisticsResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.score.APCalculationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final int SCALE = 6;

    private final ScoreRepository scoreRepository;
    private final UserCategoryStatisticsRepository statisticsRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final APCalculationService apCalculationService;
    private final OverallStatisticsService overallStatisticsService;

    @Transactional
    public UserCategoryStatisticsResponse recalculate(Long userId, UUID categoryId) {
        return recalculate(userId, categoryId, true);
    }

    @Transactional
    public UserCategoryStatisticsResponse recalculate(Long userId, UUID categoryId, boolean triggerRanking) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Category category = categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

        List<Score> scores = scoreRepository.findActiveByUserAndCategoryOrderByApDesc(userId, categoryId);
        recalculateWeightedAps(scores, category);

        BigDecimal totalAp = sumWeightedAps(scores);
        BigDecimal scoreXp = sumScoreXp(scores);
        BigDecimal averageAcc = computeAverageAcc(scores);
        BigDecimal averageAp = computeAverageAp(scores, totalAp);
        Score topPlay = scores.isEmpty() ? null : scores.get(0);

        UserCategoryStatistics current = statisticsRepository
                .findByUser_IdAndCategory_IdAndActiveTrue(userId, categoryId)
                .orElse(null);

        if (current != null) {
            current.setActive(false);
            statisticsRepository.saveAndFlush(current);
        }

        UserCategoryStatistics newStats = UserCategoryStatistics.builder()
                .user(user)
                .category(category)
                .ap(totalAp)
                .scoreXp(scoreXp)
                .averageAcc(averageAcc)
                .averageAp(averageAp)
                .rankedPlays(scores.size())
                .topPlay(topPlay)
                .supersedes(current)
                .supersedesReason("Score submission")
                .supersedesAuthor(userId)
                .active(true)
                .build();
        statisticsRepository.saveAndFlush(newStats);

        if (category.isCountForOverall()) {
            overallStatisticsService.recalculate(userId, triggerRanking);
        }

        return toResponse(newStats);
    }

    public UserCategoryStatisticsResponse findByUserAndCategoryCode(Long userId, String categoryCode) {
        UserCategoryStatistics stats = statisticsRepository
                .findByUser_IdAndCategory_CodeAndActiveTrue(userId, categoryCode)
                .orElseThrow(() -> new ResourceNotFoundException("Statistics", userId + "/" + categoryCode));
        return toResponse(stats);
    }

    private void recalculateWeightedAps(List<Score> scores, Category category) {
        for (int i = 0; i < scores.size(); i++) {
            Score s = scores.get(i);
            BigDecimal weighted = apCalculationService.calculateWeightedAP(s.getAp(), i, category.getWeightCurve());
            s.setWeightedAp(weighted);
        }
        scoreRepository.saveAll(scores);
    }

    private BigDecimal sumWeightedAps(List<Score> scores) {
        return scores.stream()
                .map(Score::getWeightedAp)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumScoreXp(List<Score> scores) {
        return scores.stream()
                .map(Score::getXpGained)
                .filter(xp -> xp != null)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal computeAverageAcc(List<Score> scores) {
        if (scores.isEmpty())
            return null;
        BigDecimal sum = scores.stream()
                .map(s -> BigDecimal.valueOf(s.getScore())
                        .divide(BigDecimal.valueOf(s.getMapDifficulty().getMaxScore()), MC))
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
        return sum.divide(BigDecimal.valueOf(scores.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal computeAverageAp(List<Score> scores, BigDecimal totalAp) {
        if (scores.isEmpty())
            return BigDecimal.ZERO;
        return totalAp.divide(BigDecimal.valueOf(scores.size()), SCALE, RoundingMode.HALF_UP);
    }

    static UserCategoryStatisticsResponse toResponse(UserCategoryStatistics s) {
        return UserCategoryStatisticsResponse.builder()
                .id(s.getId())
                .userId(String.valueOf(s.getUser().getId()))
                .categoryId(s.getCategory().getId())
                .ranking(s.getRanking())
                .countryRanking(s.getCountryRanking())
                .ap(s.getAp())
                .scoreXp(s.getScoreXp())
                .averageAcc(s.getAverageAcc())
                .averageAp(s.getAverageAp())
                .rankedPlays(s.getRankedPlays())
                .topPlayId(s.getTopPlay() != null ? s.getTopPlay().getId() : null)
                .createdAt(s.getCreatedAt())
                .build();
    }
}
