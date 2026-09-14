package com.accsaber.backend.service.stats;

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
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneSetBonusRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryRankingHistoryRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.clan.ClanPlayerStatsService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.score.APCalculationService;
import com.accsaber.backend.util.Rounding;
import com.accsaber.backend.util.TimeRangeUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

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
    private final UserMissionRepository userMissionRepository;
    private final UserCampaignScoreRepository userCampaignScoreRepository;

    private DuplicateUserService duplicateUserService;
    private ClanPlayerStatsService clanPlayerStatsService;

    @Autowired
    @Lazy
    public void setDuplicateUserService(DuplicateUserService duplicateUserService) {
        this.duplicateUserService = duplicateUserService;
    }

    @Autowired
    @Lazy
    public void setClanPlayerStatsService(ClanPlayerStatsService clanPlayerStatsService) {
        this.clanPlayerStatsService = clanPlayerStatsService;
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

        UserCategoryStatistics current = statisticsRepository
                .findActiveForUpdate(userId, categoryId)
                .orElse(null);

        List<Score> scores = scoreRepository.findActiveByUserAndCategoryOrderByApDesc(userId, categoryId);
        recalculateWeightedAps(scores, category);

        double totalAp = sumWeightedAps(scores);
        double scoreXp = sumScoreXp(scores);
        Double averageAcc = computeAverageAcc(scores);
        double averageAp = computeAverageAp(scores);
        Score topPlay = scores.isEmpty() ? null : scores.get(0);

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
                .totalMissionXp(user.getMissionXp())
                .totalCampaignXp(user.getCampaignXp())
                .clan(clanPlayerStatsService.forUser(resolved))
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
        double scoreXpDiff = scoreRepository.sumXpGainedByUserIdSince(resolved, since);
        double milestoneXpDiff = userMilestoneLinkRepository.sumMilestoneXpGainedLast24h(resolved);
        double milestoneSetBonusXpDiff = userMilestoneSetBonusRepository.sumSetBonusXpGainedLast24h(resolved);
        double missionXpDiff = userMissionRepository.sumMissionXpGainedLast24h(resolved);
        double campaignXpDiff = userCampaignScoreRepository.sumCampaignXpGainedSince(resolved, since);
        boolean hasAnyXp = scoreXpDiff > 0
                || milestoneXpDiff > 0
                || milestoneSetBonusXpDiff > 0
                || missionXpDiff > 0
                || campaignXpDiff > 0;

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
                        .missionXpDiff(missionXpDiff)
                        .campaignXpDiff(campaignXpDiff)
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
                .apDiff(Rounding.round(latest.getAp() - base.getAp(), SCALE))
                .scoreXpDiff(Rounding.round(scoreXpDiff, SCALE))
                .milestoneXpDiff(Rounding.round(milestoneXpDiff, SCALE))
                .milestoneSetBonusXpDiff(Rounding.round(milestoneSetBonusXpDiff, SCALE))
                .missionXpDiff(Rounding.round(missionXpDiff, SCALE))
                .campaignXpDiff(Rounding.round(campaignXpDiff, SCALE))
                .averageAccDiff(diffNullable(latest.getAverageAcc(), base.getAverageAcc()))
                .averageApDiff(diffNullable(latest.getAverageAp(), base.getAverageAp()))
                .rankingDiff(diffNullableInt(latest.getRanking(), base.getRanking()))
                .countryRankingDiff(diffNullableInt(latest.getCountryRanking(), base.getCountryRanking()))
                .rankedPlaysDiff(latest.getRankedPlays() - base.getRankedPlays())
                .from(base.getCreatedAt())
                .to(latest.getCreatedAt())
                .build());
    }

    private static Double diffNullable(Double a, Double b) {
        if (a == null || b == null)
            return null;
        return Rounding.round(a - b, SCALE);
    }

    private static Integer diffNullableInt(Integer a, Integer b) {
        if (a == null || b == null)
            return null;
        return a - b;
    }

    private void recalculateWeightedAps(List<Score> scores, Category category) {
        for (int i = 0; i < scores.size(); i++) {
            Score s = scores.get(i);
            s.setWeightedAp(apCalculationService.calculateWeightedAP(s.getAp(), i, category.getWeightCurve()));
        }
        scoreRepository.saveAll(scores);
    }

    private double sumWeightedAps(List<Score> scores) {
        double total = 0.0;
        for (Score s : scores) {
            total += s.getWeightedAp();
        }
        return Rounding.round(total, SCALE);
    }

    private double sumScoreXp(List<Score> scores) {
        double total = 0.0;
        for (Score s : scores) {
            if (s.getXpGained() != null) {
                total += s.getXpGained();
            }
        }
        return Rounding.round(total, SCALE);
    }

    private Double computeAverageAcc(List<Score> scores) {
        if (scores.isEmpty())
            return null;
        double sum = 0.0;
        for (Score s : scores) {
            sum += (double) s.getScore() / s.getMapDifficulty().getMaxScore();
        }
        return Rounding.round(sum / scores.size(), SCALE);
    }

    private double computeAverageAp(List<Score> scores) {
        if (scores.isEmpty())
            return 0.0;
        double sum = 0.0;
        for (Score s : scores) {
            sum += s.getAp();
        }
        return Rounding.round(sum / scores.size(), SCALE);
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
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
