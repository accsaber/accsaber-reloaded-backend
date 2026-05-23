package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategorySkill;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategorySkillRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;

@Service
public class MissionAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(MissionAssignmentService.class);
    private static final int MAX_SWAP_ATTEMPTS = 5;
    private static final int DAILY_MISSION_COUNT = 2;
    private static final int SNIPE_CANDIDATE_LIMIT = 50;
    private static final String OVERALL_CODE = "overall";

    private final UserRepository userRepository;
    private final UserCategorySkillRepository skillRepository;
    private final UserCategoryStatisticsRepository statsRepository;
    private final ScoreRepository scoreRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MissionTemplateRepository templateRepository;
    private final UserMissionRepository userMissionRepository;
    private final ItemRepository itemRepository;
    private final MissionCalibrationService calibrationService;
    private final MissionRolloverService rolloverService;
    private final TransactionTemplate transactionTemplate;
    private final Executor backfillExecutor;

    public MissionAssignmentService(
            UserRepository userRepository,
            UserCategorySkillRepository skillRepository,
            UserCategoryStatisticsRepository statsRepository,
            ScoreRepository scoreRepository,
            MapDifficultyRepository mapDifficultyRepository,
            MissionTemplateRepository templateRepository,
            UserMissionRepository userMissionRepository,
            ItemRepository itemRepository,
            MissionCalibrationService calibrationService,
            MissionRolloverService rolloverService,
            TransactionTemplate transactionTemplate,
            @Qualifier("backfillExecutor") Executor backfillExecutor) {
        this.userRepository = userRepository;
        this.skillRepository = skillRepository;
        this.statsRepository = statsRepository;
        this.scoreRepository = scoreRepository;
        this.mapDifficultyRepository = mapDifficultyRepository;
        this.templateRepository = templateRepository;
        this.userMissionRepository = userMissionRepository;
        this.itemRepository = itemRepository;
        this.calibrationService = calibrationService;
        this.rolloverService = rolloverService;
        this.transactionTemplate = transactionTemplate;
        this.backfillExecutor = backfillExecutor;
    }

    @Scheduled(cron = "${accsaber.scheduler.mission-daily-cron:0 0 4 * * *}")
    public void runDailyCron() {
        log.info("Daily mission rollover starting");
        purgeAndRollPool(MissionPool.daily, false);
    }

    @Scheduled(cron = "${accsaber.scheduler.mission-weekly-cron:0 5 4 * * MON}")
    public void runWeeklyCron() {
        log.info("Weekly mission rollover starting");
        purgeAndRollPool(MissionPool.weekly, false);
    }

    public boolean bootstrapIfEmpty() {
        if (userMissionRepository.count() > 0) {
            return false;
        }
        log.info("Mission table empty - bootstrapping daily + weekly missions for all eligible users (fresh random)");
        purgeAndRollPool(MissionPool.daily, true);
        purgeAndRollPool(MissionPool.weekly, true);
        return true;
    }

    public void assignOnLoginAsync(Long userId) {
        backfillExecutor.execute(() -> {
            try {
                MissionPoolCache cache = loadPoolCache();
                transactionTemplate.executeWithoutResult(s -> {
                    if (!hasActive(userId, MissionPool.daily)) {
                        assignForUser(userId, MissionPool.daily, cache, false);
                    }
                    if (!hasActive(userId, MissionPool.weekly)) {
                        assignForUser(userId, MissionPool.weekly, cache, false);
                    }
                });
            } catch (Exception e) {
                log.warn("Mission assignment on login failed for user {}: {}", userId, e.getMessage());
            }
        });
    }

    @Transactional
    public void assignDailyIfMissing(Long userId) {
        if (hasActive(userId, MissionPool.daily))
            return;
        assignForUser(userId, MissionPool.daily, loadPoolCache(), false);
    }

    @Transactional
    public void assignWeeklyIfMissing(Long userId) {
        if (hasActive(userId, MissionPool.weekly))
            return;
        assignForUser(userId, MissionPool.weekly, loadPoolCache(), false);
    }

    @Transactional
    public List<UserMission> regenerateForUser(Long userId, MissionPool pool) {
        userMissionRepository.voidActiveForUser(userId);
        return assignForUser(userId, pool, loadPoolCache(), true);
    }

    private boolean hasActive(Long userId, MissionPool pool) {
        return userMissionRepository.countByUser_IdAndPoolAndStatus(userId, pool, MissionStatus.active) > 0;
    }

    private MissionPoolCache loadPoolCache() {
        List<MissionTemplate> daily = templateRepository.findByPoolAndActiveTrue(MissionPool.daily);
        List<MissionTemplate> weekly = templateRepository.findByPoolAndActiveTrue(MissionPool.weekly);
        List<Item> poolable = itemRepository.findByMissionPoolableTrueAndActiveTrueAndDeprecatedFalse();
        return new MissionPoolCache(daily, weekly, poolable);
    }

    private void purgeAndRollPool(MissionPool pool, boolean freshSeed) {
        transactionTemplate.executeWithoutResult(status -> {
            userMissionRepository.expireDueByPool(Instant.now(), pool);
            userMissionRepository.deleteExpiredByPool(pool);
        });

        MissionPoolCache cache = loadPoolCache();
        List<Long> eligible = scoreRepository.findUserIdsWithAtLeastActiveScores(1);
        log.info("Rolling {} missions for {} users (fresh={})", pool, eligible.size(), freshSeed);

        for (Long userId : eligible) {
            backfillExecutor.execute(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> assignForUser(userId, pool, cache, freshSeed));
                } catch (Exception e) {
                    log.error("Mission assignment failed for user {} pool {}: {}", userId, pool, e.getMessage());
                }
            });
        }
    }

    @Transactional
    public List<UserMission> assignForUser(Long userId, MissionPool pool, MissionPoolCache cache,
            boolean freshSeed) {
        User user = userRepository.findByIdAndActiveTrue(userId).orElse(null);
        if (user == null || user.isBanned())
            return List.of();

        MissionAssignmentContext ctx = buildContext(userId);
        if (ctx.activeCategories().isEmpty())
            return List.of();

        return switch (pool) {
            case daily -> assignDaily(ctx, cache, freshSeed);
            case weekly -> assignWeekly(ctx, cache, freshSeed);
            case event -> List.of();
        };
    }

    private MissionAssignmentContext buildContext(Long userId) {
        List<UserCategoryStatistics> activeStats = statsRepository.findActiveByUser_IdWithCategory(userId).stream()
                .filter(s -> s.getRankedPlays() != null && s.getRankedPlays() > 0)
                .toList();
        if (activeStats.isEmpty()) {
            return new MissionAssignmentContext(userId, List.of(), Map.of(), BigDecimal.ZERO);
        }
        List<Category> active = activeStats.stream()
                .map(UserCategoryStatistics::getCategory)
                .filter(Category::isActive)
                .filter(c -> !OVERALL_CODE.equals(c.getCode()))
                .toList();
        Map<UUID, UserCategorySkill> skills = skillRepository.findByUserIdActive(userId).stream()
                .collect(Collectors.toMap(s -> s.getCategory().getId(), s -> s, (a, b) -> a));

        BigDecimal rollingXp = activeStats.stream()
                .map(UserCategoryStatistics::getScoreXp)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        return new MissionAssignmentContext(userId, active, skills, rollingXp);
    }

    private List<UserMission> assignDaily(MissionAssignmentContext ctx, MissionPoolCache cache, boolean freshSeed) {
        Random rng = freshSeed ? new Random() : new Random(rolloverService.deterministicSeed(
                ctx.userId(), LocalDate.now(ZoneId.systemDefault()), MissionPool.daily));
        Instant expiresAt = rolloverService.nextRollover(MissionPool.daily, Instant.now());

        Map<Boolean, List<MissionTemplate>> partitioned = cache.daily().stream()
                .collect(Collectors.partitioningBy(MissionTemplate::isGuaranteedDoable));
        List<MissionTemplate> guaranteed = partitioned.get(true);
        List<MissionTemplate> normal = partitioned.get(false);

        List<UserMission> assigned = new ArrayList<>();
        Set<UUID> usedCategoryIds = new HashSet<>();

        UserMission first = pickAndBuild(ctx, guaranteed, expiresAt, MissionPool.daily, rng, usedCategoryIds,
                cache, null);
        if (first != null) {
            assigned.add(userMissionRepository.save(first));
            if (first.getCategory() != null)
                usedCategoryIds.add(first.getCategory().getId());
        }

        for (int i = assigned.size(); i < DAILY_MISSION_COUNT; i++) {
            UserMission next = pickAndBuild(ctx, normal, expiresAt, MissionPool.daily, rng, usedCategoryIds,
                    cache, null);
            if (next == null)
                break;
            assigned.add(userMissionRepository.save(next));
            if (next.getCategory() != null)
                usedCategoryIds.add(next.getCategory().getId());
        }
        return assigned;
    }

    private List<UserMission> assignWeekly(MissionAssignmentContext ctx, MissionPoolCache cache, boolean freshSeed) {
        Random rng = freshSeed ? new Random() : new Random(rolloverService.deterministicSeed(
                ctx.userId(), LocalDate.now(ZoneId.systemDefault()), MissionPool.weekly));
        Instant expiresAt = rolloverService.nextRollover(MissionPool.weekly, Instant.now());

        List<UserMission> assigned = new ArrayList<>();
        List<Category> categories = ctx.activeCategories();
        int extremeSlot = categories.isEmpty() ? -1 : rng.nextInt(categories.size());
        Set<UUID> usedTemplateIds = new HashSet<>();

        for (int i = 0; i < categories.size(); i++) {
            MissionBand forced = i == extremeSlot ? MissionBand.extreme : null;
            UserMission mission = pickAndBuildForCategory(ctx, cache.weekly(), categories.get(i), expiresAt,
                    MissionPool.weekly, rng, cache, forced, usedTemplateIds);
            if (mission != null) {
                assigned.add(userMissionRepository.save(mission));
                usedTemplateIds.add(mission.getTemplate().getId());
            }
        }
        return assigned;
    }

    private UserMission pickAndBuild(MissionAssignmentContext ctx, List<MissionTemplate> pool,
            Instant expiresAt, MissionPool poolType, Random rng, Set<UUID> excludeCategories,
            MissionPoolCache cache, MissionBand forcedBand) {
        if (pool.isEmpty())
            return null;
        for (int attempt = 0; attempt < MAX_SWAP_ATTEMPTS; attempt++) {
            MissionTemplate template = weightedPick(pool, rng);
            if (template == null)
                return null;
            Category category = pickCategoryForType(ctx, template, rng, excludeCategories);
            UserMission built = buildMission(ctx, template, category, expiresAt, poolType, rng, cache, forcedBand);
            if (built != null)
                return built;
        }
        return null;
    }

    private UserMission pickAndBuildForCategory(MissionAssignmentContext ctx, List<MissionTemplate> pool,
            Category category, Instant expiresAt, MissionPool poolType, Random rng, MissionPoolCache cache,
            MissionBand forcedBand, Set<UUID> excludeTemplateIds) {
        if (pool.isEmpty())
            return null;
        List<MissionTemplate> shuffled = pool.stream()
                .filter(t -> excludeTemplateIds == null || !excludeTemplateIds.contains(t.getId()))
                .filter(t -> forcedBand != MissionBand.extreme
                        || (t.getType() != MissionType.PLAY_N_MAPS && t.getType() != MissionType.XP_IN_WINDOW))
                .collect(Collectors.toCollection(ArrayList::new));
        if (shuffled.isEmpty())
            shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, rng);
        for (int attempt = 0; attempt < Math.min(MAX_SWAP_ATTEMPTS, shuffled.size()); attempt++) {
            MissionTemplate template = shuffled.get(attempt);
            UserMission built = buildMission(ctx, template, category, expiresAt, poolType, rng, cache, forcedBand);
            if (built != null)
                return built;
        }
        return null;
    }

    private Category pickCategoryForType(MissionAssignmentContext ctx, MissionTemplate template, Random rng,
            Set<UUID> exclude) {
        if (template.getType() == MissionType.XP_IN_WINDOW) {
            return null;
        }
        if (template.getType() == MissionType.SCORES_N) {
            return null;
        }
        if (template.getType() == MissionType.PLAY_N_MAPS && template.getCode() != null
                && template.getCode().endsWith("_any")) {
            return null;
        }
        if (requiresMapPick(template.getType())) {
            List<Category> nonOverall = ctx.activeCategories().stream()
                    .filter(c -> !OVERALL_CODE.equals(c.getCode()))
                    .filter(c -> !exclude.contains(c.getId()))
                    .toList();
            if (nonOverall.isEmpty()) {
                nonOverall = ctx.activeCategories().stream()
                        .filter(c -> !OVERALL_CODE.equals(c.getCode()))
                        .toList();
            }
            return nonOverall.isEmpty() ? null : nonOverall.get(rng.nextInt(nonOverall.size()));
        }
        List<Category> filtered = ctx.activeCategories().stream()
                .filter(c -> !exclude.contains(c.getId()))
                .toList();
        if (filtered.isEmpty())
            filtered = ctx.activeCategories();
        return filtered.get(rng.nextInt(filtered.size()));
    }

    private UserMission buildMission(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, Random rng, MissionPoolCache cache, MissionBand forcedBand) {
        if (category == null && template.getType() != MissionType.XP_IN_WINDOW
                && template.getType() != MissionType.PLAY_N_MAPS
                && template.getType() != MissionType.SCORES_N)
            return null;
        MissionBand band = forcedBand != null ? forcedBand : pickBand(rng);
        return switch (template.getType()) {
            case PLAY_N_MAPS -> buildPlayNMaps(ctx, template, category, expiresAt, pool, band, rng, cache);
            case XP_IN_WINDOW -> buildXpInWindow(ctx, template, category, expiresAt, pool, band, rng, cache);
            case ACC_ON_MAP -> buildAccOnMap(ctx, template, category, expiresAt, pool, band, rng, cache);
            case AP_ON_MAP -> buildApOnMap(ctx, template, category, expiresAt, pool, band, rng, cache);
            case PB_SPECIFIC_MAP -> buildPbSpecificMap(ctx, template, category, expiresAt, pool, band, rng, cache);
            case PB_ABOVE_THRESHOLD -> buildPbAboveThreshold(ctx, template, category, expiresAt, pool, band, rng, cache);
            case SNIPE_PLAYER_ON_MAP -> buildSnipe(ctx, template, category, expiresAt, pool, band, rng, cache);
            case STREAK_ON_MAP -> buildStreakOnMap(ctx, template, category, expiresAt, pool, band, rng, cache);
            case STREAK_N_IN_CATEGORY -> buildStreakNInCategory(ctx, template, category, expiresAt, pool, band, rng, cache);
            case COMEBACK_PB -> buildComebackPb(ctx, template, category, expiresAt, pool, band, rng, cache);
            case SCORES_N -> buildScoresN(ctx, template, category, expiresAt, pool, band, rng, cache);
        };
    }

    private UserMission buildPlayNMaps(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        int count = pickCount(template, band, rng);
        int xp = calibrationService.computeXpReward(template,
                skillLevelFor(ctx, category), band, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetCount(count)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildXpInWindow(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        BigDecimal multiplier = calibrationService.bandMultiplier(template, band);
        BigDecimal rolling = ctx.rollingDailyXp() == null ? BigDecimal.valueOf(500) : ctx.rollingDailyXp();
        int targetXp = rolling.multiply(multiplier).max(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        int xp = Math.max(50, calibrationService.computeXpReward(template,
                skillLevelFor(ctx, category), band, null));
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetXp(targetXp)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildAccOnMap(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        MapTargetResult result = computeMapTarget(ctx, template, category, band, rng);
        if (result == null)
            return null;
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetMapDifficulty(result.pick().difficulty())
                .targetAcc(result.targetAcc())
                .targetScore(result.targetScore())
                .xpReward(result.xpReward())
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildApOnMap(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        MapTargetResult result = computeMapTarget(ctx, template, category, band, rng);
        if (result == null)
            return null;
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetMapDifficulty(result.pick().difficulty())
                .targetAp(result.targetRawAp())
                .targetScore(result.targetScore())
                .xpReward(result.xpReward())
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private MapTargetResult computeMapTarget(MissionAssignmentContext ctx, MissionTemplate template,
            Category category, MissionBand band, Random rng) {
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        if (skill == null || skill.getRawApForOneGain() == null)
            return null;
        Curve scoreCurve = category.getScoreCurve();
        if (scoreCurve == null)
            return null;
        BigDecimal threshold = skill.getRawApForOneGain();
        BigDecimal multiplier = calibrationService.bandMultiplier(template, band);
        BigDecimal targetRawAp = calibrationService.targetRawAp(threshold, multiplier);
        targetRawAp = capExtremeAtTopAp(targetRawAp, band, skill);
        MapPick pick = sampleEligibleMap(category, threshold, multiplier, scoreCurve, rng);
        if (pick == null)
            return null;
        targetRawAp = capAtMapRealisticCeiling(targetRawAp, pick, scoreCurve, band);
        targetRawAp = floorAtUserScoreOnMap(targetRawAp, ctx.userId(), pick.difficulty().getId());
        BigDecimal targetAcc = calibrationService.targetAccuracy(targetRawAp, pick.complexity(), scoreCurve,
                skill.getTopAp());
        Integer targetScore = pick.maxScore() != null
                ? BigDecimal.valueOf(pick.maxScore()).multiply(targetAcc)
                        .setScale(0, RoundingMode.HALF_UP).intValue()
                : null;
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, null);
        return new MapTargetResult(pick, targetRawAp, targetAcc, targetScore, xp);
    }

    private BigDecimal skillLevelFor(MissionAssignmentContext ctx, Category category) {
        if (category != null) {
            UserCategorySkill s = ctx.skillByCategoryId().get(category.getId());
            if (s != null && s.getSkillLevel() != null)
                return s.getSkillLevel();
        }
        return ctx.skillByCategoryId().values().stream()
                .filter(s -> s.getCategory() != null && OVERALL_CODE.equals(s.getCategory().getCode()))
                .filter(s -> s.getSkillLevel() != null)
                .findFirst()
                .map(UserCategorySkill::getSkillLevel)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal ageAdjustedUserAp(Score myScore, BigDecimal topAp) {
        BigDecimal scoreAp = myScore.getAp() != null ? myScore.getAp() : BigDecimal.ZERO;
        Instant when = myScore.getTimeSet() != null ? myScore.getTimeSet() : myScore.getCreatedAt();
        if (when == null || topAp == null || topAp.compareTo(scoreAp) <= 0) {
            return scoreAp;
        }
        long days = java.time.Duration.between(when, Instant.now()).toDays();
        if (days <= 0) {
            return scoreAp;
        }
        double agingFactor = Math.max(0.0, Math.min(1.0, (365.0 - days) / 365.0));
        double liftWeight = (1.0 - agingFactor) * 0.20;
        if (liftWeight <= 0) {
            return scoreAp;
        }
        BigDecimal lift = topAp.subtract(scoreAp).multiply(BigDecimal.valueOf(liftWeight));
        return scoreAp.add(lift);
    }

    private BigDecimal capExtremeAtTopAp(BigDecimal targetRawAp, MissionBand band, UserCategorySkill skill) {
        if (band != MissionBand.extreme || skill.getTopAp() == null || skill.getTopAp().signum() <= 0) {
            return targetRawAp;
        }
        BigDecimal cap = skill.getTopAp().multiply(new BigDecimal("1.05"));
        return targetRawAp.min(cap);
    }

    private double pbFreshnessBoost(Score existing) {
        if (existing == null)
            return 1.0;
        Instant when = existing.getTimeSet() != null ? existing.getTimeSet() : existing.getCreatedAt();
        if (when == null)
            return 1.0;
        long days = java.time.Duration.between(when, Instant.now()).toDays();
        if (days <= 0)
            return 1.30;
        double freshness = Math.max(0.0, Math.min(1.0, (180.0 - days) / 180.0));
        return 1.0 + freshness * 0.30;
    }

    private BigDecimal floorAtUserScoreOnMap(BigDecimal targetRawAp, Long userId, UUID mapDifficultyId) {
        Optional<Score> existing = scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                userId, mapDifficultyId);
        if (existing.isEmpty() || existing.get().getAp() == null) {
            return targetRawAp;
        }
        BigDecimal floor = existing.get().getAp().add(BigDecimal.ONE);
        return targetRawAp.max(floor);
    }

    private BigDecimal capAtMapRealisticCeiling(BigDecimal targetRawAp, MapPick pick, Curve scoreCurve,
            MissionBand band) {
        BigDecimal wr = scoreRepository.findMaxApByMapDifficulty(pick.difficulty().getId());
        BigDecimal bandFraction = switch (band) {
            case easy -> new BigDecimal("0.82");
            case medium -> new BigDecimal("0.90");
            case hard -> new BigDecimal("0.96");
            case extreme -> new BigDecimal("1.02");
        };
        if (wr != null && wr.signum() > 0) {
            return targetRawAp.min(wr.multiply(bandFraction));
        }
        BigDecimal fallback = calibrationService.maxRealisticRawAp(pick.complexity(), scoreCurve);
        if (fallback == null || fallback.signum() <= 0) {
            return targetRawAp;
        }
        return targetRawAp.min(fallback.multiply(bandFraction));
    }

    private UserMission buildPbSpecificMap(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        if (skill == null || skill.getRawApForOneGain() == null || category.getScoreCurve() == null)
            return null;
        BigDecimal threshold = skill.getRawApForOneGain();
        BigDecimal multiplier = calibrationService.bandMultiplier(template, band);
        MapPick pick = sampleEligibleMap(category, threshold, multiplier, category.getScoreCurve(), rng);
        if (pick == null)
            return null;
        BigDecimal targetRawAp = calibrationService.targetRawAp(threshold, multiplier);
        targetRawAp = capExtremeAtTopAp(targetRawAp, band, skill);
        targetRawAp = capAtMapRealisticCeiling(targetRawAp, pick, category.getScoreCurve(), band);
        Optional<Score> existing = scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                ctx.userId(), pick.difficulty().getId());
        if (existing.isPresent() && existing.get().getAp().compareTo(targetRawAp) >= 0) {
            targetRawAp = existing.get().getAp().add(BigDecimal.ONE);
        }
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, null);
        xp = (int) Math.round(xp * pbFreshnessBoost(existing.orElse(null)));
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetMapDifficulty(pick.difficulty())
                .targetAp(targetRawAp)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildPbAboveThreshold(MissionAssignmentContext ctx, MissionTemplate template,
            Category category, Instant expiresAt, MissionPool pool, MissionBand band, Random rng,
            MissionPoolCache cache) {
        List<Score> scores = scoreRepository.findActiveByUserAndCategoryOrderByApDesc(ctx.userId(), category.getId());
        if (scores.isEmpty())
            return null;
        int idx = Math.min(scores.size() - 1, (int) (scores.size() * 0.5));
        BigDecimal threshold = scores.get(idx).getAp();
        BigDecimal targetRawAp = calibrationService.targetRawAp(threshold,
                calibrationService.bandMultiplier(template, band));
        int count = pickCount(template, band, rng);
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetThresholdAp(threshold)
                .targetAp(targetRawAp)
                .targetCount(count)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildSnipe(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        if (skill == null)
            return null;
        MapPick pick = sampleAnyRankedMap(category, rng);
        if (pick == null)
            return null;

        Optional<Score> mine = scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                ctx.userId(), pick.difficulty().getId());
        int baselineScore;
        if (mine.isPresent()) {
            baselineScore = mine.get().getScore();
        } else {
            baselineScore = 0;
        }

        List<Score> candidates = scoreRepository.findSnipeCandidatesAboveScore(
                pick.difficulty().getId(), ctx.userId(), baselineScore,
                PageRequest.of(0, SNIPE_CANDIDATE_LIMIT));
        if (candidates.isEmpty())
            return null;

        int bandIndex = snipeBandIndex(band, candidates.size(), skillLevelFor(ctx, category));
        Score target = candidates.get(bandIndex);
        BigDecimal effectiveUserAp = mine.map(s -> ageAdjustedUserAp(s, skill.getTopAp()))
                .orElse(BigDecimal.ZERO);
        BigDecimal snipeDistance = target.getAp().subtract(effectiveUserAp).max(BigDecimal.ZERO);

        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, snipeDistance);
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetMapDifficulty(pick.difficulty())
                .targetPlayer(target.getUser())
                .targetScore(target.getScore())
                .targetAp(target.getAp())
                .snipeDistance(snipeDistance)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private MapPick sampleAnyRankedMap(Category category, Random rng) {
        List<Object[]> rows = mapDifficultyRepository.findRankedWithComplexityInRange(
                category.getId(), BigDecimal.ZERO, new BigDecimal("9999"));
        if (rows.isEmpty())
            return null;
        Object[] row = rows.get(rng.nextInt(rows.size()));
        MapDifficulty diff = (MapDifficulty) row[0];
        BigDecimal complexity = (BigDecimal) row[1];
        return new MapPick(diff, complexity, diff.getMaxScore());
    }

    private MapPick sampleEligibleMap(Category category, BigDecimal threshold, BigDecimal multiplier,
            Curve scoreCurve, Random rng) {
        var range = calibrationService.complexityRange(threshold, multiplier, scoreCurve);
        if (range == null)
            return null;
        List<Object[]> rows = mapDifficultyRepository.findRankedWithComplexityInRange(
                category.getId(), range.min(), range.max());
        if (rows.isEmpty())
            return null;
        Object[] row = rows.get(rng.nextInt(rows.size()));
        MapDifficulty diff = (MapDifficulty) row[0];
        BigDecimal complexity = (BigDecimal) row[1];
        return new MapPick(diff, complexity, diff.getMaxScore());
    }

    private int pickCount(MissionTemplate template, MissionBand band, Random rng) {
        int min = template.getTargetCountMin() != null ? template.getTargetCountMin() : 1;
        int max = template.getTargetCountMax() != null ? template.getTargetCountMax() : Math.max(min + 1, 10);
        int spread = Math.max(1, max - min);
        double centerFrac = switch (band) {
            case easy -> 0.17;
            case medium -> 0.50;
            case hard -> 0.83;
            case extreme -> 1.00;
        };
        int center = min + (int) Math.round(spread * centerFrac);
        int jitterRange = Math.max(1, spread / 6);
        int jitter = rng.nextInt(jitterRange * 2 + 1) - jitterRange;
        return Math.min(max, Math.max(min, center + jitter));
    }

    private MissionBand pickBand(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 30)
            return MissionBand.easy;
        if (roll < 70)
            return MissionBand.medium;
        if (roll < 95)
            return MissionBand.hard;
        return MissionBand.extreme;
    }

    private MissionTemplate weightedPick(List<MissionTemplate> templates, Random rng) {
        int total = 0;
        for (MissionTemplate t : templates)
            total += t.getWeight();
        if (total <= 0)
            return null;
        int roll = rng.nextInt(total);
        int acc = 0;
        for (MissionTemplate t : templates) {
            acc += t.getWeight();
            if (roll < acc)
                return t;
        }
        return templates.get(templates.size() - 1);
    }

    private Item rollItemReward(MissionTemplate template, Random rng, MissionPoolCache cache) {
        if (template.getAwardsItem() != null) {
            return template.getAwardsItem();
        }
        List<Item> pool = cache.poolableItems();
        if (pool.isEmpty())
            return null;
        if (rng.nextInt(100) >= 15)
            return null;
        return pool.get(rng.nextInt(pool.size()));
    }

    private UserMission.UserMissionBuilder baseBuilder(MissionAssignmentContext ctx, MissionTemplate template,
            Category category, Instant expiresAt, MissionPool pool, MissionBand band) {
        return UserMission.builder()
                .user(userRepository.getReferenceById(ctx.userId()))
                .template(template)
                .pool(pool)
                .category(category)
                .band(band)
                .expiresAt(expiresAt);
    }

    private int snipeBandIndex(MissionBand band, int size, BigDecimal skillLevel) {
        int last = size - 1;
        int baseIndex = baseSnipeBandIndex(band, size);
        double skill = skillLevel != null ? skillLevel.doubleValue() : 0.0;
        double promoter = Math.max(0.0, Math.min(1.0, (skill - 75.0) / 25.0));
        if (promoter <= 0.0) {
            return baseIndex;
        }
        int headroom = last - baseIndex;
        int promoted = baseIndex + (int) Math.round(headroom * promoter);
        return Math.min(last, promoted);
    }

    private int baseSnipeBandIndex(MissionBand band, int size) {
        int last = size - 1;
        if (size <= 5) {
            return switch (band) {
                case easy -> 0;
                case medium -> Math.min(last, 1);
                case hard -> Math.min(last, 1);
                case extreme -> Math.min(last, 2);
            };
        }
        if (size <= 15) {
            return switch (band) {
                case easy -> 0;
                case medium -> Math.min(last, 1);
                case hard -> Math.min(last, 3);
                case extreme -> Math.min(last, 4);
            };
        }
        if (size <= 30) {
            return switch (band) {
                case easy -> 0;
                case medium -> 2;
                case hard -> 5;
                case extreme -> 7;
            };
        }
        return switch (band) {
            case easy -> 0;
            case medium -> 3;
            case hard -> 8;
            case extreme -> 14;
        };
    }

    private boolean requiresMapPick(MissionType type) {
        return switch (type) {
            case ACC_ON_MAP, AP_ON_MAP, PB_SPECIFIC_MAP, SNIPE_PLAYER_ON_MAP, STREAK_ON_MAP, COMEBACK_PB -> true;
            default -> false;
        };
    }

    private UserMission buildScoresN(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        int count = pickCount(template, band, rng);
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetCount(count)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildComebackPb(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        Instant olderThan = Instant.now().minus(java.time.Duration.ofDays(365));
        List<Score> oldScores = scoreRepository.findActiveByUserAndCategoryOlderThan(
                ctx.userId(), category.getId(), olderThan);
        if (oldScores.isEmpty())
            return null;
        Score chosen = oldScores.get(rng.nextInt(oldScores.size()));
        BigDecimal bandMult = calibrationService.bandMultiplier(template, band);
        BigDecimal targetRawAp = chosen.getAp().multiply(bandMult).max(chosen.getAp().add(BigDecimal.ONE));
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        if (skill != null) {
            targetRawAp = capExtremeAtTopAp(targetRawAp, band, skill);
        }
        MapPick pick = new MapPick(chosen.getMapDifficulty(), null, chosen.getMapDifficulty().getMaxScore());
        targetRawAp = capAtMapRealisticCeiling(targetRawAp, pick, category.getScoreCurve(), band);
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetMapDifficulty(chosen.getMapDifficulty())
                .targetAp(targetRawAp)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildStreakNInCategory(MissionAssignmentContext ctx, MissionTemplate template,
            Category category, Instant expiresAt, MissionPool pool, MissionBand band, Random rng,
            MissionPoolCache cache) {
        Integer topStreak = scoreRepository.findMaxStreak115ByUserAndCategoryActive(ctx.userId(), category.getId());
        if (topStreak == null || topStreak < 3)
            return null;
        BigDecimal bandMult = calibrationService.bandMultiplier(template, band);
        int targetStreak = BigDecimal.valueOf(topStreak)
                .multiply(new BigDecimal("0.65"))
                .multiply(bandMult)
                .setScale(0, RoundingMode.HALF_UP).intValue();
        targetStreak = Math.max(2, targetStreak);
        int count = pickCount(template, band, rng);
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetStreak(targetStreak)
                .targetCount(count)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildStreakOnMap(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        if (skill == null || skill.getRawApForOneGain() == null || category.getScoreCurve() == null)
            return null;
        Integer topStreak = scoreRepository.findMaxStreak115ByUserAndCategoryActive(ctx.userId(), category.getId());
        if (topStreak == null || topStreak < 3)
            return null;
        MapPick pick = sampleEligibleMap(category, skill.getRawApForOneGain(),
                calibrationService.bandMultiplier(template, band), category.getScoreCurve(), rng);
        if (pick == null)
            return null;
        BigDecimal bandMult = calibrationService.bandMultiplier(template, band);
        int targetStreak = BigDecimal.valueOf(topStreak)
                .multiply(new BigDecimal("0.85"))
                .multiply(bandMult)
                .setScale(0, RoundingMode.HALF_UP).intValue();
        targetStreak = Math.max(2, targetStreak);
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetMapDifficulty(pick.difficulty())
                .targetStreak(targetStreak)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private record MapPick(MapDifficulty difficulty, BigDecimal complexity, Integer maxScore) {
    }

    private record MapTargetResult(MapPick pick, BigDecimal targetRawAp, BigDecimal targetAcc,
            Integer targetScore, int xpReward) {
    }

    public record MissionPoolCache(List<MissionTemplate> daily, List<MissionTemplate> weekly,
            List<Item> poolableItems) {
    }
}
