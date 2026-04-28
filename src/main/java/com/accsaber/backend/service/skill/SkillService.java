package com.accsaber.backend.service.skill;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.event.EventListener;
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
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.score.APCalculationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final UserCategoryStatisticsRepository statsRepository;
    private final ScoreRepository scoreRepository;
    private final APCalculationService apCalculationService;
    private final SkillProperties skillProperties;
    private final CacheManager cacheManager;

    @Cacheable(value = "skill", key = "#userId + ':' + (#categoryCode == null ? 'all' : #categoryCode)")
    public SkillResponse computeSkillForUser(Long userId, String categoryCode) {
        User user = requireUser(userId);
        List<Category> categories = (categoryCode == null || categoryCode.isBlank())
                ? categoryRepository.findByActiveTrue()
                : List.of(requireCategory(categoryCode));

        List<SkillCategoryResponse> skills = categories.stream()
                .map(c -> computeForCategory(user.getId(), c))
                .toList();

        return SkillResponse.builder()
                .userId(String.valueOf(user.getId()))
                .skills(skills)
                .build();
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

    public void evictForUser(Long userId) {
        Cache cache = cacheManager.getCache("skill");
        if (!(cache instanceof CaffeineCache caffeine)) {
            return;
        }
        String prefix = userId + ":";
        caffeine.getNativeCache().asMap().keySet()
                .removeIf(k -> k instanceof String s && s.startsWith(prefix));
    }

    @CacheEvict(value = "skill", allEntries = true)
    public void evictAll() {
        log.info("Evicted all skill caches");
    }

    @EventListener
    public void onScoreSubmitted(ScoreSubmittedEvent event) {
        try {
            evictForUser(Long.parseLong(event.score().getUserId()));
        } catch (NumberFormatException e) {
            log.warn("ScoreSubmittedEvent carried non-numeric userId '{}'; skipping skill cache eviction",
                    event.score().getUserId());
        }
    }

    private SkillCategoryResponse computeForCategory(Long userId, Category category) {
        Optional<UserCategoryStatistics> statsOpt = statsRepository
                .findByUser_IdAndCategory_IdAndActiveTrue(userId, category.getId());
        Integer rank = statsOpt.map(UserCategoryStatistics::getRanking).orElse(null);
        Score topPlay = statsOpt.map(UserCategoryStatistics::getTopPlay).orElse(null);
        BigDecimal topAp = topPlay != null ? topPlay.getAp() : BigDecimal.ZERO;

        long activePlayers = statsRepository.countActivePlayersInCategory(category.getId());
        boolean hasCurve = category.getWeightCurve() != null;
        BigDecimal rawApForOneGain = hasCurve ? computeRawApForOneGain(userId, category) : null;

        double peak = sigmoidScore(topAp.doubleValue(),
                skillProperties.getPeakCenter(), skillProperties.getPeakSpread());
        double sustained = hasCurve
                ? sigmoidScore(rawApForOneGain.doubleValue(),
                        skillProperties.getSustainedCenter(), skillProperties.getSustainedSpread())
                : 0;
        double combined = hasCurve ? harmonicMean(sustained, peak) : peak;
        double rankScore = rankScore(rank, activePlayers);
        double skill = skillProperties.getRankWeight() * rankScore
                + skillProperties.getCombinedWeight() * combined;

        SkillComponents components = SkillComponents.builder()
                .rank(round1(rankScore))
                .sustained(hasCurve ? round1(sustained) : 0)
                .peak(round1(peak))
                .combined(round1(combined))
                .rawApForOneGain(rawApForOneGain)
                .topAp(topAp)
                .categoryRank(rank)
                .activePlayers(activePlayers)
                .build();

        return SkillCategoryResponse.builder()
                .categoryCode(category.getCode())
                .categoryName(category.getName())
                .skillLevel(round1(skill))
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

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
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
