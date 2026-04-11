package com.accsaber.backend.service.stats;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.RankingHistoryResponse;
import com.accsaber.backend.model.dto.response.player.StatsDiffResponse;
import com.accsaber.backend.model.dto.response.player.UserAllStatisticsResponse;
import com.accsaber.backend.model.dto.response.player.UserCategoryStatisticsResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneSetBonusRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryRankingHistoryRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.score.APCalculationService;
import com.accsaber.backend.util.TimeRangeUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final int SCALE = 6;

    private final ScoreRepository scoreRepository;
    private final UserCategoryStatisticsRepository statisticsRepository;
    private final UserCategoryRankingHistoryRepository rankingHistoryRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final APCalculationService apCalculationService;
    private final OverallStatisticsService overallStatisticsService;
    private final UserMilestoneLinkRepository userMilestoneLinkRepository;
    private final UserMilestoneSetBonusRepository userMilestoneSetBonusRepository;

    private DuplicateUserService duplicateUserService;

    @Autowired
    @Lazy
    public void setDuplicateUserService(DuplicateUserService duplicateUserService) {
        this.duplicateUserService = duplicateUserService;
    }

    @Transactional
    public UserCategoryStatisticsResponse recalculate(Long userId, UUID categoryId) {
        return recalculate(userId, categoryId, true);
    }

    @Transactional
    public UserCategoryStatisticsResponse recalculate(Long userId, UUID categoryId, boolean triggerRanking) {
        return recalculate(userId, categoryId, triggerRanking, true);
    }

    @Transactional
    public UserCategoryStatisticsResponse recalculate(Long userId, UUID categoryId, boolean triggerRanking,
            boolean triggerOverall) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Category category = categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

        List<Score> scores = scoreRepository.findActiveByUserAndCategoryOrderByApDesc(userId, categoryId);
        recalculateWeightedAps(scores, category);

        BigDecimal totalAp = sumWeightedAps(scores);
        BigDecimal scoreXp = sumScoreXp(scores);
        BigDecimal averageAcc = computeAverageAcc(scores);
        BigDecimal averageAp = computeAverageAp(scores);
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
                .ranking(current != null ? current.getRanking() : null)
                .countryRanking(current != null ? current.getCountryRanking() : null)
                .rankedPlays(scores.size())
                .topPlay(topPlay)
                .supersedes(current)
                .supersedesReason("Score submission")
                .supersedesAuthor(userId)
                .active(true)
                .build();
        statisticsRepository.saveAndFlush(newStats);

        if (category.isCountForOverall() && triggerOverall) {
            overallStatisticsService.recalculate(userId, triggerRanking);
        }

        return toResponse(newStats);
    }

    public List<UserCategoryStatisticsResponse> findCategoryStatsByUser(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        return statisticsRepository.findByUser_IdAndActiveTrue(resolved).stream()
                .map(StatisticsService::toResponse)
                .toList();
    }

    public UserAllStatisticsResponse findAllByUser(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        User user = userRepository.findByIdAndActiveTrue(resolved)
                .orElseThrow(() -> new ResourceNotFoundException("User", resolved));
        return UserAllStatisticsResponse.builder()
                .totalXp(user.getTotalXp())
                .totalScoreXp(scoreRepository.sumXpGainedByUserId(resolved))
                .totalMilestoneXp(userMilestoneLinkRepository.sumCompletedMilestoneXpByUserId(resolved))
                .totalMilestoneSetBonusXp(userMilestoneSetBonusRepository.sumSetBonusXpByUserId(resolved))
                .categories(findCategoryStatsByUser(userId))
                .build();
    }

    public UserCategoryStatisticsResponse findByUserAndCategoryCode(Long userId, String categoryCode) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        UserCategoryStatistics stats = statisticsRepository
                .findByUser_IdAndCategory_CodeAndActiveTrue(resolved, categoryCode)
                .orElseThrow(() -> new ResourceNotFoundException("Statistics", resolved + "/" + categoryCode));
        return toResponse(stats);
    }

    public List<UserCategoryStatisticsResponse> findHistoric(Long userId, String categoryCode, int amount,
            String unit) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Instant since = TimeRangeUtil.computeSince(amount, unit);
        return statisticsRepository
                .findHistoricDownsampled(resolved, categoryCode, since)
                .stream()
                .map(StatisticsService::toResponse)
                .toList();
    }

    public List<RankingHistoryResponse> findRankingHistory(Long userId, String categoryCode, int amount,
            String unit) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Instant since = TimeRangeUtil.computeSince(amount, unit);
        return rankingHistoryRepository
                .findByUserAndCategoryCodeSince(resolved, categoryCode, since)
                .stream()
                .map(h -> new RankingHistoryResponse(h.getRanking(), h.getCountryRanking(), h.getRecordedAt()))
                .toList();
    }

    public Optional<StatsDiffResponse> computeStatsDiff(Long userId, String categoryCode) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);

        Instant since = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
        BigDecimal scoreXpDiff = scoreRepository.sumXpGainedByUserIdSince(resolved, since);
        BigDecimal milestoneXpDiff = userMilestoneLinkRepository.sumMilestoneXpGainedLast24h(resolved);
        BigDecimal milestoneSetBonusXpDiff = userMilestoneSetBonusRepository.sumSetBonusXpGainedLast24h(resolved);
        boolean hasAnyXp = scoreXpDiff.compareTo(BigDecimal.ZERO) > 0
                || milestoneXpDiff.compareTo(BigDecimal.ZERO) > 0
                || milestoneSetBonusXpDiff.compareTo(BigDecimal.ZERO) > 0;

        Optional<UserCategoryStatistics> baseOpt = statisticsRepository
                .findLatestBeforeLastDay(resolved, categoryCode);
        Optional<UserCategoryStatistics> latestOpt = statisticsRepository
                .findMostRecent(resolved, categoryCode);

        if (baseOpt.isEmpty() || latestOpt.isEmpty()) {
            if (hasAnyXp) {
                return Optional.of(StatsDiffResponse.builder()
                        .scoreXpDiff(scoreXpDiff)
                        .milestoneXpDiff(milestoneXpDiff)
                        .milestoneSetBonusXpDiff(milestoneSetBonusXpDiff)
                        .from(since)
                        .to(Instant.now())
                        .build());
            }
            return Optional.empty();
        }

        UserCategoryStatistics base = baseOpt.get();
        UserCategoryStatistics latest = latestOpt.get();

        return Optional.of(StatsDiffResponse.builder()
                .categoryId(latest.getCategory().getId())
                .apDiff(latest.getAp().subtract(base.getAp()))
                .scoreXpDiff(scoreXpDiff)
                .milestoneXpDiff(milestoneXpDiff)
                .milestoneSetBonusXpDiff(milestoneSetBonusXpDiff)
                .averageAccDiff(diffNullable(latest.getAverageAcc(), base.getAverageAcc()))
                .averageApDiff(diffNullable(latest.getAverageAp(), base.getAverageAp()))
                .rankingDiff(diffNullableInt(latest.getRanking(), base.getRanking()))
                .countryRankingDiff(diffNullableInt(latest.getCountryRanking(), base.getCountryRanking()))
                .rankedPlaysDiff(latest.getRankedPlays() - base.getRankedPlays())
                .from(base.getCreatedAt())
                .to(latest.getCreatedAt())
                .build());
    }

    private static BigDecimal diffNullable(BigDecimal a, BigDecimal b) {
        if (a == null || b == null)
            return null;
        return a.subtract(b);
    }

    private static Integer diffNullableInt(Integer a, Integer b) {
        if (a == null || b == null)
            return null;
        return a - b;
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

    private BigDecimal computeAverageAp(List<Score> scores) {
        if (scores.isEmpty())
            return BigDecimal.ZERO;
        BigDecimal sum = scores.stream()
                .map(Score::getAp)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
        return sum.divide(BigDecimal.valueOf(scores.size()), SCALE, RoundingMode.HALF_UP);
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
