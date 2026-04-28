package com.accsaber.backend.service.skill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.SkillProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.ApToNextResponse;
import com.accsaber.backend.model.dto.response.player.SkillCategoryResponse;
import com.accsaber.backend.model.dto.response.player.SkillCategoryResponse.SkillComponents;
import com.accsaber.backend.model.dto.response.player.SkillResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategorySkill;
import com.accsaber.backend.model.entity.user.UserCategorySkillSnapshot;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategorySkillRepository;
import com.accsaber.backend.repository.user.UserCategorySkillSnapshotRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.score.APCalculationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillService {

    private static final String OVERALL_CODE = "overall";
    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final UserCategoryStatisticsRepository statsRepository;
    private final ScoreRepository scoreRepository;
    private final UserCategorySkillRepository skillRepository;
    private final UserCategorySkillSnapshotRepository snapshotRepository;
    private final APCalculationService apCalculationService;
    private final SkillProperties skillProperties;

    @Transactional
    public SkillResponse computeSkillForUser(Long userId, String categoryCode) {
        User user = requireUser(userId);

        if (categoryCode == null || categoryCode.isBlank()) {
            List<UserCategorySkill> rows = skillRepository.findByUserIdActive(userId);
            if (rows.isEmpty()) {
                upsertAllForUser(userId);
                rows = skillRepository.findByUserIdActive(userId);
            }
            return toResponse(user, rows);
        }

        Category category = requireCategory(categoryCode);
        UserCategorySkill row = skillRepository
                .findByUserIdAndCategoryId(userId, category.getId())
                .orElseGet(() -> {
                    upsertAllForUser(userId);
                    return skillRepository.findByUserIdAndCategoryId(userId, category.getId())
                            .orElseThrow(() -> new ResourceNotFoundException("UserCategorySkill",
                                    userId + "/" + category.getCode()));
                });
        return toResponse(user, List.of(row));
    }

    public ApToNextResponse calculateApToNext(Long userId, String categoryCode) {
        User user = requireUser(userId);
        Category category = requireCategory(categoryCode);
        BigDecimal raw = computeRawApForOneGain(user.getId(), category);
        return ApToNextResponse.builder()
                .userId(String.valueOf(user.getId()))
                .categoryCode(category.getCode())
                .rawApForOneGain(raw)
                .build();
    }

    @Transactional
    public void upsertSkill(Long userId, UUID categoryId) {
        User user = userRepository.findByIdAndActiveTrue(userId).orElse(null);
        if (user == null) {
            return;
        }
        Category category = categoryRepository.findByIdAndActiveTrue(categoryId).orElse(null);
        if (category == null) {
            return;
        }
        if (OVERALL_CODE.equals(category.getCode())) {
            persistOverall(user, category);
        } else {
            persistSingle(user, category);
            categoryRepository.findByCodeAndActiveTrue(OVERALL_CODE)
                    .ifPresent(overall -> persistOverall(user, overall));
        }
    }

    @Transactional
    public void upsertAllForUser(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId).orElse(null);
        if (user == null) {
            return;
        }
        Category overall = null;
        for (Category c : categoryRepository.findByActiveTrue()) {
            if (OVERALL_CODE.equals(c.getCode())) {
                overall = c;
            } else {
                persistSingle(user, c);
            }
        }
        if (overall != null) {
            persistOverall(user, overall);
        }
    }

    @Scheduled(cron = "0 0 4 * * MON")
    @Transactional
    public void captureWeeklySnapshots() {
        List<UserCategorySkill> all = skillRepository.findAll();
        int inserted = 0;
        for (UserCategorySkill skill : all) {
            if (snapshotChanged(skill)) {
                snapshotRepository.save(UserCategorySkillSnapshot.builder()
                        .user(skill.getUser())
                        .category(skill.getCategory())
                        .skillLevel(skill.getSkillLevel())
                        .rankScore(skill.getRankScore())
                        .sustainedScore(skill.getSustainedScore())
                        .peakScore(skill.getPeakScore())
                        .combinedScore(skill.getCombinedScore())
                        .build());
                inserted++;
            }
        }
        log.info("Weekly skill snapshot: inserted {} of {} rows", inserted, all.size());
    }

    private boolean snapshotChanged(UserCategorySkill skill) {
        Optional<UserCategorySkillSnapshot> latest = snapshotRepository
                .findFirstByUser_IdAndCategory_IdOrderByCapturedAtDesc(
                        skill.getUser().getId(), skill.getCategory().getId());
        if (latest.isEmpty()) {
            return true;
        }
        UserCategorySkillSnapshot s = latest.get();
        return s.getSkillLevel().compareTo(skill.getSkillLevel()) != 0
                || s.getRankScore().compareTo(skill.getRankScore()) != 0
                || s.getSustainedScore().compareTo(skill.getSustainedScore()) != 0
                || s.getPeakScore().compareTo(skill.getPeakScore()) != 0
                || s.getCombinedScore().compareTo(skill.getCombinedScore()) != 0;
    }

    private void persistSingle(User user, Category category) {
        Optional<UserCategoryStatistics> statsOpt = statsRepository
                .findByUser_IdAndCategory_IdAndActiveTrue(user.getId(), category.getId());
        Integer rank = statsOpt.map(UserCategoryStatistics::getRanking).orElse(null);
        Score topPlay = statsOpt.map(UserCategoryStatistics::getTopPlay).orElse(null);
        BigDecimal topAp = topPlay != null ? topPlay.getAp() : BigDecimal.ZERO;

        long activePlayers = statsRepository.countActivePlayersInCategory(category.getId());
        BigDecimal rawApForOneGain = computeRawApForOneGain(user.getId(), category);

        double peak = computePeakScore(topAp, category);
        double sustained = rawApForOneGain != null
                ? sigmoidScore(rawApForOneGain.doubleValue(),
                        skillProperties.getSustainedCenter(), skillProperties.getSustainedSpread())
                : 0;
        double combined = rawApForOneGain != null ? harmonicMean(sustained, peak) : peak;
        double rankScore = rankScore(rank, activePlayers);
        double skill = skillProperties.getRankWeight() * rankScore
                + skillProperties.getCombinedWeight() * combined;

        save(user, category, skill, rankScore, sustained, peak, combined, rawApForOneGain, topAp,
                rank, activePlayers);
    }

    private void persistOverall(User user, Category overall) {
        List<UserCategorySkill> contributors = skillRepository.findByUserIdForOverall(user.getId());

        Optional<UserCategoryStatistics> statsOpt = statsRepository
                .findByUser_IdAndCategory_IdAndActiveTrue(user.getId(), overall.getId());
        Integer rank = statsOpt.map(UserCategoryStatistics::getRanking).orElse(null);
        long activePlayers = statsRepository.countActivePlayersInCategory(overall.getId());

        double rankScore = rankScore(rank, activePlayers);

        if (contributors.isEmpty()) {
            save(user, overall, rankScore * skillProperties.getRankWeight(),
                    rankScore, 0, 0, 0, null, BigDecimal.ZERO, rank, activePlayers);
            return;
        }

        double sustained = mean(contributors, UserCategorySkill::getSustainedScore);
        double peak = mean(contributors, UserCategorySkill::getPeakScore);
        double combined = mean(contributors, UserCategorySkill::getCombinedScore);
        double aggregatedSkill = mean(contributors, UserCategorySkill::getSkillLevel);
        BigDecimal topAp = contributors.stream()
                .map(UserCategorySkill::getTopAp)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        save(user, overall, aggregatedSkill, rankScore, sustained, peak, combined, null, topAp,
                rank, activePlayers);
    }

    private void save(User user, Category category, double skill, double rank, double sustained,
            double peak, double combined, BigDecimal rawApForOneGain, BigDecimal topAp,
            Integer categoryRank, long activePlayers) {
        UserCategorySkill row = skillRepository
                .findByUserIdAndCategoryId(user.getId(), category.getId())
                .orElseGet(() -> UserCategorySkill.builder()
                        .user(user)
                        .category(category)
                        .build());
        row.setSkillLevel(round2(skill));
        row.setRankScore(round2(rank));
        row.setSustainedScore(round2(sustained));
        row.setPeakScore(round2(peak));
        row.setCombinedScore(round2(combined));
        row.setRawApForOneGain(rawApForOneGain);
        row.setTopAp(topAp);
        row.setCategoryRank(categoryRank);
        row.setActivePlayers(activePlayers);
        skillRepository.save(row);
    }

    private SkillResponse toResponse(User user, List<UserCategorySkill> rows) {
        return SkillResponse.builder()
                .userId(String.valueOf(user.getId()))
                .skills(rows.stream().map(this::toCategoryResponse).toList())
                .build();
    }

    private SkillCategoryResponse toCategoryResponse(UserCategorySkill row) {
        SkillComponents components = SkillComponents.builder()
                .rank(row.getRankScore().doubleValue())
                .sustained(row.getSustainedScore().doubleValue())
                .peak(row.getPeakScore().doubleValue())
                .combined(row.getCombinedScore().doubleValue())
                .rawApForOneGain(row.getRawApForOneGain())
                .topAp(row.getTopAp())
                .categoryRank(row.getCategoryRank())
                .activePlayers(row.getActivePlayers())
                .build();
        return SkillCategoryResponse.builder()
                .categoryCode(row.getCategory().getCode())
                .categoryName(row.getCategory().getName())
                .skillLevel(row.getSkillLevel().doubleValue())
                .components(components)
                .build();
    }

    private BigDecimal computeRawApForOneGain(Long userId, Category category) {
        if (category.getWeightCurve() == null) {
            return null;
        }
        List<BigDecimal> rawAps = scoreRepository
                .findActiveByUserAndCategoryOrderByApDesc(userId, category.getId())
                .stream()
                .map(Score::getAp)
                .toList();
        return apCalculationService.calculateRawApForOneWeightedGain(rawAps, category.getWeightCurve());
    }

    double computePeakScore(BigDecimal topAp, Category category) {
        if (topAp == null || topAp.signum() <= 0) {
            return 0;
        }
        double rawSigmoid = sigmoidScore(topAp.doubleValue(),
                skillProperties.getPeakCenter(), skillProperties.getPeakSpread());
        BigDecimal max = scoreRepository.findMaxApInCategory(category.getId());
        if (max == null || max.signum() <= 0) {
            return rawSigmoid;
        }
        double leaderSigmoid = sigmoidScore(max.doubleValue(),
                skillProperties.getPeakCenter(), skillProperties.getPeakSpread());
        if (leaderSigmoid <= 0) {
            return rawSigmoid;
        }
        double ratio = Math.min(1.0, topAp.doubleValue() / max.doubleValue());
        double relativeFactor = Math.pow(ratio, skillProperties.getPeakRelativeAlpha());
        return rawSigmoid * relativeFactor / leaderSigmoid * 100.0;
    }

    double sigmoidScore(double value, double center, double spread) {
        double z = (value - center) / spread;
        return 100.0 / (1.0 + Math.exp(-z));
    }

    double harmonicMean(double a, double b) {
        if (a <= 0 || b <= 0) {
            return 0;
        }
        return (2 * a * b) / (a + b);
    }

    double rankScore(Integer rank, long activePlayers) {
        if (rank == null || rank < 1 || activePlayers <= 1) {
            return 0;
        }
        if (rank == 1) {
            return 100.0;
        }
        double clampedRank = Math.min(rank, activePlayers);
        double base = 1 - Math.log10(clampedRank) / Math.log10(activePlayers);
        if (base <= 0) {
            return 0;
        }
        return 100.0 * Math.pow(base, skillProperties.getRankCurveExponent());
    }

    private double mean(List<UserCategorySkill> rows, Function<UserCategorySkill, BigDecimal> field) {
        return rows.stream().mapToDouble(r -> field.apply(r).doubleValue()).average().orElse(0);
    }

    private BigDecimal round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private User requireUser(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private Category requireCategory(String code) {
        return categoryRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new ResourceNotFoundException("Category", code));
    }
}
