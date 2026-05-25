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
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int DAILY_MISSION_COUNT = 2;
    private static final int SNIPE_CANDIDATE_LIMIT = 50;
    private static final String OVERALL_CODE = "overall";
    private static final ThreadLocal<String> LAST_FAIL_REASON = new ThreadLocal<>();

    private static <T> T failBuild(String reason) {
        LAST_FAIL_REASON.set(reason);
        return null;
    }

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
        rolloutAllUsers(true);
        return true;
    }

    public void rolloutAllUsers(boolean freshSeed) {
        MissionPoolCache cache = loadPoolCache();
        List<Long> eligible = scoreRepository.findActivePlayerIdsWithAtLeastActiveScores(1);
        log.info("Rolling out daily + weekly for {} users (fresh={})", eligible.size(), freshSeed);

        for (Long userId : eligible) {
            backfillExecutor.execute(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        userMissionRepository.deleteActiveForUser(userId);
                        assignForUser(userId, MissionPool.daily, cache, freshSeed);
                        assignForUser(userId, MissionPool.weekly, cache, freshSeed);
                    });
                } catch (Exception e) {
                    log.error("Rollout failed for user {}: {}", userId, e.getMessage());
                }
            });
        }
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
        userMissionRepository.deleteActiveForUser(userId);
        return assignForUser(userId, pool, loadPoolCache(), true);
    }

    private boolean hasActive(Long userId, MissionPool pool) {
        return userMissionRepository.countByUser_IdAndPoolAndStatus(userId, pool, MissionStatus.active) > 0;
    }

    private MissionPoolCache loadPoolCache() {
        List<MissionTemplate> daily = templateRepository.findByPoolAndActiveTrue(MissionPool.daily);
        List<MissionTemplate> weekly = templateRepository.findByPoolAndActiveTrue(MissionPool.weekly);
        List<Item> poolable = itemRepository.findByMissionPoolableTrueAndActiveTrueAndDeprecatedFalse();
        return new MissionPoolCache(daily, weekly, poolable, new ConcurrentHashMap<>());
    }

    private void purgeAndRollPool(MissionPool pool, boolean freshSeed) {
        transactionTemplate.executeWithoutResult(status -> {
            int removed = userMissionRepository.deleteNonCompletedByPool(pool);
            log.info("Purged {} non-completed {} missions before rollout", removed, pool);
        });

        MissionPoolCache cache = loadPoolCache();
        List<Long> eligible = scoreRepository.findActivePlayerIdsWithAtLeastActiveScores(1);
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
            return new MissionAssignmentContext(userId, List.of(), Map.of(), Map.of(), BigDecimal.ZERO);
        }
        List<Category> active = activeStats.stream()
                .map(UserCategoryStatistics::getCategory)
                .filter(Category::isActive)
                .filter(c -> !OVERALL_CODE.equals(c.getCode()))
                .toList();
        Map<UUID, UserCategorySkill> skills = skillRepository.findByUserIdActive(userId).stream()
                .collect(Collectors.toMap(s -> s.getCategory().getId(), s -> s, (a, b) -> a));
        Map<UUID, Long> rankedPlays = activeStats.stream()
                .filter(s -> !OVERALL_CODE.equals(s.getCategory().getCode()))
                .collect(Collectors.toMap(
                        s -> s.getCategory().getId(),
                        s -> s.getRankedPlays().longValue(),
                        (a, b) -> a));

        BigDecimal rollingXp = activeStats.stream()
                .map(UserCategoryStatistics::getScoreXp)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        return new MissionAssignmentContext(userId, active, skills, rankedPlays, rollingXp);
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
        if (pool.isEmpty()) {
            log.warn("Mission template pool empty pool={} forcedBand={}", poolType, forcedBand);
            return null;
        }
        Set<UUID> triedTemplateIds = new HashSet<>();
        List<String> attempts = log.isDebugEnabled() ? new ArrayList<>() : null;
        while (triedTemplateIds.size() < pool.size()) {
            MissionTemplate template = weightedPickExcluding(pool, rng, triedTemplateIds);
            if (template == null)
                break;
            triedTemplateIds.add(template.getId());
            Category category = pickCategoryForType(ctx, template, rng, excludeCategories);
            LAST_FAIL_REASON.remove();
            UserMission built = buildMission(ctx, template, category, expiresAt, poolType, rng, cache, forcedBand);
            if (built != null)
                return built;
            if (attempts != null) {
                String reason = LAST_FAIL_REASON.get();
                attempts.add(template.getCode() + (category != null ? "/" + category.getCode() : "/-")
                        + ":" + (reason != null ? reason : "no-reason"));
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Mission slot empty user={} pool={} forcedBand={} reason=all-templates-exhausted attempts={}",
                    ctx.userId(), poolType, forcedBand, attempts);
        }
        return null;
    }

    private UserMission pickAndBuildForCategory(MissionAssignmentContext ctx, List<MissionTemplate> pool,
            Category category, Instant expiresAt, MissionPool poolType, Random rng, MissionPoolCache cache,
            MissionBand forcedBand, Set<UUID> excludeTemplateIds) {
        if (pool.isEmpty()) {
            log.warn("Mission template pool empty pool={} category={} forcedBand={}",
                    poolType, category.getCode(), forcedBand);
            return null;
        }
        List<MissionTemplate> shuffled = pool.stream()
                .filter(t -> excludeTemplateIds == null || !excludeTemplateIds.contains(t.getId()))
                .filter(t -> forcedBand != MissionBand.extreme
                        || (t.getType() != MissionType.PLAY_N_MAPS && t.getType() != MissionType.XP_IN_WINDOW
                                && t.getType() != MissionType.SCORES_N
                                && t.getType() != MissionType.COMEBACK_PB))
                .collect(Collectors.toCollection(ArrayList::new));
        if (shuffled.isEmpty())
            shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, rng);
        List<String> tried = log.isDebugEnabled() ? new ArrayList<>() : null;
        for (MissionTemplate template : shuffled) {
            LAST_FAIL_REASON.remove();
            UserMission built = buildMission(ctx, template, category, expiresAt, poolType, rng, cache, forcedBand);
            if (built != null)
                return built;
            if (tried != null) {
                String reason = LAST_FAIL_REASON.get();
                tried.add(template.getCode() + ":" + (reason != null ? reason : "no-reason"));
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Mission slot empty user={} pool={} category={} forcedBand={} reason=all-templates-exhausted attempts={}",
                    ctx.userId(), poolType, category.getCode(), forcedBand, tried);
        }
        return null;
    }

    private MissionTemplate weightedPickExcluding(List<MissionTemplate> pool, Random rng, Set<UUID> exclude) {
        int total = 0;
        for (MissionTemplate t : pool) {
            if (!exclude.contains(t.getId()))
                total += t.getWeight();
        }
        if (total <= 0)
            return null;
        int roll = rng.nextInt(total);
        int acc = 0;
        for (MissionTemplate t : pool) {
            if (exclude.contains(t.getId()))
                continue;
            acc += t.getWeight();
            if (roll < acc)
                return t;
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
            return failBuild("no-category-for-type");
        MissionBand band = forcedBand != null ? forcedBand : pickBand(rng);
        boolean bandForced = forcedBand != null;
        return switch (template.getType()) {
            case PLAY_N_MAPS -> buildPlayNMaps(ctx, template, category, expiresAt, pool, band, rng, cache);
            case XP_IN_WINDOW -> buildXpInWindow(ctx, template, category, expiresAt, pool, band, rng, cache);
            case ACC_ON_MAP -> buildAccOnMap(ctx, template, category, expiresAt, pool, band, rng, cache, bandForced);
            case AP_ON_MAP -> buildApOnMap(ctx, template, category, expiresAt, pool, band, rng, cache, bandForced);
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
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache,
            boolean bandForced) {
        MapTargetResult result = computeMapTarget(ctx, template, category, band, rng, cache, bandForced);
        if (result == null)
            return null;
        return baseBuilder(ctx, template, category, expiresAt, pool, result.effectiveBand())
                .targetMapDifficulty(result.pick().difficulty())
                .targetAcc(result.targetAcc())
                .targetScore(result.targetScore())
                .xpReward(result.xpReward())
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildApOnMap(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache,
            boolean bandForced) {
        MapTargetResult result = computeMapTarget(ctx, template, category, band, rng, cache, bandForced);
        if (result == null)
            return null;
        return baseBuilder(ctx, template, category, expiresAt, pool, result.effectiveBand())
                .targetMapDifficulty(result.pick().difficulty())
                .targetAp(result.targetRawAp())
                .targetScore(result.targetScore())
                .xpReward(result.xpReward())
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private MapTargetResult computeMapTarget(MissionAssignmentContext ctx, MissionTemplate template,
            Category category, MissionBand band, Random rng, MissionPoolCache cache, boolean bandForced) {
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        if (skill == null || skill.getRawApForOneGain() == null)
            return failBuild("no-skill-or-threshold");
        Curve scoreCurve = category.getScoreCurve();
        if (scoreCurve == null)
            return failBuild("no-score-curve");
        BigDecimal threshold = liftedThreshold(ctx, category, skill.getRawApForOneGain());
        BigDecimal pickMultiplier = calibrationService.bandMultiplier(template, band);
        MapPick pick = sampleEligibleMap(category, threshold, pickMultiplier, scoreCurve, rng);
        if (pick == null)
            return failBuild("no-eligible-map");

        MissionBand effectiveBand = band;
        Optional<Score> existing = scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                ctx.userId(), pick.difficulty().getId());
        if (!bandForced && existing.isPresent() && existing.get().getWeightedAp() != null) {
            BigDecimal maxWeightedAp = scoreRepository.findMaxWeightedApByUserAndCategory(
                    ctx.userId(), category.getId());
            MissionBand derived = bandFromWeightedRatio(existing.get().getWeightedAp(), maxWeightedAp);
            effectiveBand = blendBands(band, derived);
        }

        BigDecimal effectiveMultiplier = calibrationService.bandMultiplier(template, effectiveBand);
        BigDecimal skillAnchored = calibrationService.targetRawAp(threshold, effectiveMultiplier);
        BigDecimal existingAp = existing.map(Score::getAp).orElse(null);
        BigDecimal liftedFloor = existingAp != null
                ? calibrationService.bandLiftedFloorAp(existingAp, pick.complexity(), scoreCurve, effectiveBand)
                : null;
        BigDecimal targetRawAp = skillAnchored;
        if (liftedFloor != null) {
            targetRawAp = targetRawAp.max(liftedFloor);
        }
        targetRawAp = capExtremeAtTopAp(targetRawAp, effectiveBand, skill);
        targetRawAp = capAtMapRealisticCeiling(targetRawAp, pick, scoreCurve, effectiveBand, cache,
                skillLevelFor(ctx, category));

        if (existingAp != null && targetRawAp.compareTo(existingAp) <= 0) {
            return failBuild("target-below-existing-after-caps");
        }
        BigDecimal minMeaningful = (effectiveBand == MissionBand.hard || effectiveBand == MissionBand.extreme)
                && skill.getTopAp() != null
                ? skill.getTopAp().multiply(new BigDecimal("0.70"))
                : skillAnchored.multiply(new BigDecimal("0.80"));
        if (existingAp == null && targetRawAp.compareTo(minMeaningful) < 0) {
            return failBuild("target-below-min-meaningful");
        }

        BigDecimal targetAcc = calibrationService.targetAccuracy(targetRawAp, pick.complexity(), scoreCurve,
                skill.getTopAp());
        Integer targetScore = pick.maxScore() != null
                ? BigDecimal.valueOf(pick.maxScore()).multiply(targetAcc)
                        .setScale(0, RoundingMode.HALF_UP).intValue()
                : null;
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), effectiveBand, null);
        return new MapTargetResult(pick, targetRawAp, targetAcc, targetScore, xp, effectiveBand);
    }

    private MissionBand blendBands(MissionBand assigned, MissionBand derived) {
        if (assigned == null)
            return derived;
        if (derived == null)
            return assigned;
        double blended = 0.6 * assigned.ordinal() + 0.4 * derived.ordinal();
        int idx = (int) Math.round(blended);
        MissionBand[] all = MissionBand.values();
        return all[Math.min(all.length - 1, Math.max(0, idx))];
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

    private BigDecimal liftedThreshold(MissionAssignmentContext ctx, Category targetCategory,
            BigDecimal categoryThreshold) {
        if (categoryThreshold == null || targetCategory == null) {
            return categoryThreshold;
        }
        UserCategorySkill targetSkill = ctx.skillByCategoryId().get(targetCategory.getId());
        BigDecimal targetSkillLevel = targetSkill != null && targetSkill.getSkillLevel() != null
                ? targetSkill.getSkillLevel()
                : BigDecimal.ZERO;

        UserCategorySkill bestOther = ctx.skillByCategoryId().values().stream()
                .filter(s -> s.getCategory() != null)
                .filter(s -> !OVERALL_CODE.equals(s.getCategory().getCode()))
                .filter(s -> !s.getCategory().getId().equals(targetCategory.getId()))
                .filter(s -> s.getRawApForOneGain() != null && s.getSkillLevel() != null)
                .max(java.util.Comparator.comparing(UserCategorySkill::getSkillLevel))
                .orElse(null);
        if (bestOther == null) {
            return categoryThreshold;
        }
        BigDecimal skillGap = bestOther.getSkillLevel().subtract(targetSkillLevel);
        if (skillGap.compareTo(new BigDecimal("10")) < 0) {
            return categoryThreshold;
        }
        BigDecimal bestThreshold = bestOther.getRawApForOneGain();
        if (bestThreshold.compareTo(categoryThreshold) <= 0) {
            return categoryThreshold;
        }

        BigDecimal liftFraction;
        if (skillGap.compareTo(new BigDecimal("40")) >= 0) {
            liftFraction = new BigDecimal("0.85");
        } else if (skillGap.compareTo(new BigDecimal("25")) >= 0) {
            liftFraction = new BigDecimal("0.65");
        } else {
            liftFraction = new BigDecimal("0.40");
        }

        Long targetPlays = ctx.rankedPlaysByCategoryId().get(targetCategory.getId());
        Long bestPlays = ctx.rankedPlaysByCategoryId().get(bestOther.getCategory().getId());
        if (targetPlays != null && bestPlays != null && bestPlays > 0) {
            double playRatio = targetPlays.doubleValue() / bestPlays.doubleValue();
            if (playRatio >= 1.0) {
                return categoryThreshold;
            }
            double playDampen = Math.max(0.30, 1.0 - playRatio * 0.7);
            liftFraction = liftFraction.multiply(BigDecimal.valueOf(playDampen));
        }

        BigDecimal gap = bestThreshold.subtract(categoryThreshold);
        return categoryThreshold.add(gap.multiply(liftFraction));
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
        if (skill.getTopAp() == null || skill.getTopAp().signum() <= 0) {
            return targetRawAp;
        }
        BigDecimal factor = switch (band) {
            case extreme -> new BigDecimal("1.02");
            case hard -> new BigDecimal("0.95");
            default -> null;
        };
        if (factor == null) {
            return targetRawAp;
        }
        BigDecimal cap = skill.getTopAp().multiply(factor);
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

    private BigDecimal capAtMapRealisticCeiling(BigDecimal targetRawAp, MapPick pick, Curve scoreCurve,
            MissionBand band, MissionPoolCache cache, BigDecimal skillLevel) {
        BigDecimal wr = cache.mapWrApByDifficulty().computeIfAbsent(pick.difficulty().getId(), id -> {
            BigDecimal val = scoreRepository.findMaxApByMapDifficulty(id);
            return val != null ? val : BigDecimal.ZERO;
        });
        if (wr.signum() == 0) {
            wr = null;
        }
        BigDecimal bandFraction = skillAwareBandFraction(band, skillLevel);
        if (wr != null && wr.signum() > 0) {
            return targetRawAp.min(wr.multiply(bandFraction));
        }
        BigDecimal fallback = calibrationService.maxRealisticRawAp(pick.complexity(), scoreCurve);
        if (fallback == null || fallback.signum() <= 0) {
            return targetRawAp;
        }
        return targetRawAp.min(fallback.multiply(bandFraction));
    }

    private BigDecimal liftedSkillLevel(MissionAssignmentContext ctx, Category targetCategory,
            BigDecimal categorySkillLevel) {
        if (categorySkillLevel == null || targetCategory == null) {
            return categorySkillLevel;
        }
        UserCategorySkill bestOther = ctx.skillByCategoryId().values().stream()
                .filter(s -> s.getCategory() != null)
                .filter(s -> !OVERALL_CODE.equals(s.getCategory().getCode()))
                .filter(s -> !s.getCategory().getId().equals(targetCategory.getId()))
                .filter(s -> s.getSkillLevel() != null)
                .max(java.util.Comparator.comparing(UserCategorySkill::getSkillLevel))
                .orElse(null);
        if (bestOther == null)
            return categorySkillLevel;
        BigDecimal skillGap = bestOther.getSkillLevel().subtract(categorySkillLevel);
        if (skillGap.compareTo(new BigDecimal("10")) < 0)
            return categorySkillLevel;

        BigDecimal liftFraction;
        if (skillGap.compareTo(new BigDecimal("40")) >= 0) {
            liftFraction = new BigDecimal("0.45");
        } else if (skillGap.compareTo(new BigDecimal("25")) >= 0) {
            liftFraction = new BigDecimal("0.30");
        } else {
            liftFraction = new BigDecimal("0.18");
        }

        Long targetPlays = ctx.rankedPlaysByCategoryId().get(targetCategory.getId());
        Long bestPlays = ctx.rankedPlaysByCategoryId().get(bestOther.getCategory().getId());
        if (targetPlays != null && bestPlays != null && bestPlays > 0) {
            double playRatio = targetPlays.doubleValue() / bestPlays.doubleValue();
            if (playRatio >= 1.0)
                return categorySkillLevel;
            double playDampen = Math.max(0.30, 1.0 - playRatio * 0.7);
            liftFraction = liftFraction.multiply(BigDecimal.valueOf(playDampen));
        }
        return categorySkillLevel.add(skillGap.multiply(liftFraction));
    }

    private BigDecimal skillAwareBandFraction(MissionBand band, BigDecimal skillLevel) {
        double skill = skillLevel != null ? Math.min(100.0, Math.max(0.0, skillLevel.doubleValue())) : 50.0;
        double skillAdj = Math.max(0.0, (skill - 50.0) / 50.0);
        double frac = switch (band) {
            case easy -> 0.50 + skillAdj * 0.12;
            case medium -> 0.58 + skillAdj * 0.17;
            case hard -> 0.66 + skillAdj * 0.22;
            case extreme -> 0.74 + skillAdj * 0.26;
        };
        return BigDecimal.valueOf(frac);
    }

    private UserMission buildPbSpecificMap(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        if (skill == null || skill.getRawApForOneGain() == null || category.getScoreCurve() == null)
            return failBuild("no-skill-or-threshold-or-curve");
        Curve scoreCurve = category.getScoreCurve();
        BigDecimal threshold = liftedThreshold(ctx, category, skill.getRawApForOneGain());
        BigDecimal multiplier = calibrationService.bandMultiplier(template, band);
        MapPick pick = sampleEligibleMap(category, threshold, multiplier, scoreCurve, rng);
        if (pick == null)
            return failBuild("no-eligible-map");
        BigDecimal skillAnchored = calibrationService.targetRawAp(threshold, multiplier);
        Optional<Score> existing = scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                ctx.userId(), pick.difficulty().getId());
        BigDecimal existingAp = existing.map(Score::getAp).orElse(null);
        BigDecimal liftedFloor = existingAp != null
                ? calibrationService.bandLiftedFloorAp(existingAp, pick.complexity(), scoreCurve, band)
                : null;
        BigDecimal targetRawAp = skillAnchored;
        if (liftedFloor != null) {
            targetRawAp = targetRawAp.max(liftedFloor);
        }
        targetRawAp = capExtremeAtTopAp(targetRawAp, band, skill);
        targetRawAp = capAtMapRealisticCeiling(targetRawAp, pick, scoreCurve, band, cache,
                skillLevelFor(ctx, category));

        if (existingAp != null && targetRawAp.compareTo(existingAp) <= 0) {
            return failBuild("target-below-existing-after-caps");
        }
        BigDecimal minMeaningful = (band == MissionBand.hard || band == MissionBand.extreme)
                && skill.getTopAp() != null
                ? skill.getTopAp().multiply(new BigDecimal("0.70"))
                : skillAnchored.multiply(new BigDecimal("0.80"));
        if (existingAp == null && targetRawAp.compareTo(minMeaningful) < 0) {
            return failBuild("target-below-min-meaningful");
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
        if (scores.size() < 5)
            return failBuild("too-few-scores-in-category");
        double percentile = switch (band) {
            case easy -> 0.70;
            case medium -> 0.45;
            case hard -> 0.22;
            case extreme -> 0.10;
        };
        int idx = Math.min(scores.size() - 1, Math.max(0, (int) Math.round(scores.size() * percentile)));
        BigDecimal anchor = scores.get(idx).getAp();
        BigDecimal thresholdShift = switch (band) {
            case easy -> new BigDecimal("0.98");
            case medium -> BigDecimal.ONE;
            case hard -> new BigDecimal("1.015");
            case extreme -> new BigDecimal("1.02");
        };
        BigDecimal rawThreshold = anchor.multiply(thresholdShift).setScale(0, RoundingMode.HALF_UP);
        BigDecimal topAp = scores.get(0).getAp();
        BigDecimal hardCap = topAp.multiply(new BigDecimal("0.97")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal threshold = rawThreshold.compareTo(hardCap) > 0 ? hardCap : rawThreshold;
        long qualifying = scores.stream()
                .filter(s -> s.getAp() != null && s.getAp().compareTo(threshold) >= 0)
                .count();
        if (qualifying < 2)
            return failBuild("too-few-qualifying-above-threshold");
        int desiredCount = pickCount(template, band, rng);
        int maxFeasibleCount = (int) Math.floor(qualifying / 2.0);
        int count = Math.min(desiredCount, Math.max(1, maxFeasibleCount));
        if (count <= 0)
            return failBuild("count-clamp-zero");
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetThresholdAp(threshold)
                .targetCount(count)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildSnipe(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        if (skill == null || skill.getRawApForOneGain() == null || category.getScoreCurve() == null)
            return failBuild("no-skill-or-threshold-or-curve");

        Curve scoreCurve = category.getScoreCurve();
        BigDecimal threshold = liftedThreshold(ctx, category, skill.getRawApForOneGain());
        BigDecimal bandMult = calibrationService.bandMultiplier(template, band);

        BigDecimal userSkillLevel = skillLevelFor(ctx, category);
        BigDecimal effectiveUserSkill = liftedSkillLevel(ctx, category, userSkillLevel);
        double userSkillVal = effectiveUserSkill != null ? effectiveUserSkill.doubleValue() : 50.0;

        double maxSkillDistance = switch (band) {
            case easy -> 5.0;
            case medium -> 8.0;
            case hard -> 12.0;
            case extreme -> 18.0;
        };
        int rankFloorIdx = switch (band) {
            case easy -> 0;
            case medium -> 2;
            case hard -> 7;
            case extreme -> 19;
        };
        int rankCeilIdx = switch (band) {
            case easy -> 2;
            case medium -> 7;
            case hard -> 19;
            case extreme -> 49;
        };

        MapPick pick = null;
        Score target = null;
        Optional<Score> mine = Optional.empty();
        for (int attempt = 0; attempt < 8; attempt++) {
            MapPick candidate = sampleEligibleMap(category, threshold, bandMult, scoreCurve, rng);
            if (candidate == null)
                break;
            Optional<Score> myScore = scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                    ctx.userId(), candidate.difficulty().getId());
            int baseline = myScore.map(Score::getScore).orElse(0);
            BigDecimal userCurrentAp = myScore.map(Score::getAp).orElse(BigDecimal.ZERO);

            BigDecimal apCap = userCurrentAp.signum() > 0
                    ? calibrationService.bandLiftedFloorAp(userCurrentAp, candidate.complexity(), scoreCurve, band)
                    : null;

            List<Object[]> candidateRows = scoreRepository.findSnipeCandidatesAboveBaselineWithSkill(
                    candidate.difficulty().getId(), ctx.userId(), baseline, category.getId(),
                    PageRequest.of(0, SNIPE_CANDIDATE_LIMIT));

            List<Score> skillFiltered = new ArrayList<>();
            for (Object[] row : candidateRows) {
                Score s = (Score) row[0];
                BigDecimal candidateSkill = (BigDecimal) row[1];
                double skillDist = Math.abs(candidateSkill.doubleValue() - userSkillVal);
                if (skillDist > maxSkillDistance)
                    continue;
                if (apCap != null && s.getAp() != null && s.getAp().compareTo(apCap) > 0)
                    continue;
                skillFiltered.add(s);
            }
            if (skillFiltered.isEmpty())
                continue;

            int lo = Math.min(rankFloorIdx, skillFiltered.size() - 1);
            int hi = Math.min(rankCeilIdx, skillFiltered.size() - 1);
            if (lo > hi)
                continue;
            List<Score> bandSlice = skillFiltered.subList(lo, hi + 1);

            int jitter = Math.min(3, bandSlice.size());
            pick = candidate;
            target = bandSlice.get(rng.nextInt(jitter));
            mine = myScore;
            break;
        }
        if (pick == null || target == null)
            return failBuild("no-snipe-candidate-within-band");

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

    private boolean requiresMapPick(MissionType type) {
        return switch (type) {
            case ACC_ON_MAP, AP_ON_MAP, PB_SPECIFIC_MAP, SNIPE_PLAYER_ON_MAP, STREAK_ON_MAP, COMEBACK_PB -> true;
            default -> false;
        };
    }

    private UserMission buildScoresN(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        MissionBand effectiveBand = rng.nextBoolean() ? MissionBand.easy : MissionBand.medium;
        int min = template.getTargetCountMin() != null ? template.getTargetCountMin() : 1;
        int max = template.getTargetCountMax() != null ? template.getTargetCountMax() : 3;
        int count = min + rng.nextInt(Math.max(1, max - min + 1));
        int baseXp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), effectiveBand, null);
        int xp = (int) Math.round(baseXp * (0.5 + 0.5 * count));
        return baseBuilder(ctx, template, category, expiresAt, pool, effectiveBand)
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
            return failBuild("no-old-scores-for-comeback");
        Score chosen = oldScores.get(rng.nextInt(oldScores.size()));
        BigDecimal maxWeightedAp = scoreRepository.findMaxWeightedApByUserAndCategory(
                ctx.userId(), category.getId());
        MissionBand effectiveBand = bandFromWeightedRatio(chosen.getWeightedAp(), maxWeightedAp);
        BigDecimal bandMult = calibrationService.bandMultiplier(template, effectiveBand);
        BigDecimal targetRawAp = chosen.getAp().multiply(bandMult).max(chosen.getAp().add(BigDecimal.ONE));
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        if (skill != null) {
            targetRawAp = capExtremeAtTopAp(targetRawAp, effectiveBand, skill);
        }
        MapPick pick = new MapPick(chosen.getMapDifficulty(), null, chosen.getMapDifficulty().getMaxScore());
        targetRawAp = capAtMapRealisticCeiling(targetRawAp, pick, category.getScoreCurve(), effectiveBand, cache,
                skillLevelFor(ctx, category));
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), effectiveBand, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, effectiveBand)
                .targetMapDifficulty(chosen.getMapDifficulty())
                .targetAp(targetRawAp)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private MissionBand bandFromWeightedRatio(BigDecimal weighted, BigDecimal maxWeighted) {
        if (weighted == null || maxWeighted == null || maxWeighted.signum() <= 0) {
            return MissionBand.medium;
        }
        double ratio = weighted.doubleValue() / maxWeighted.doubleValue();
        if (ratio >= 0.80)
            return MissionBand.extreme;
        if (ratio >= 0.40)
            return MissionBand.hard;
        if (ratio >= 0.10)
            return MissionBand.medium;
        return MissionBand.easy;
    }

    private UserMission buildStreakNInCategory(MissionAssignmentContext ctx, MissionTemplate template,
            Category category, Instant expiresAt, MissionPool pool, MissionBand band, Random rng,
            MissionPoolCache cache) {
        Integer topStreak = scoreRepository.findMaxStreak115ByUserAndCategoryActive(ctx.userId(), category.getId());
        if (topStreak == null || topStreak < 3)
            return failBuild("user-streak-too-low");
        BigDecimal skillLevel = skillLevelFor(ctx, category);
        double skill = skillLevel != null ? skillLevel.doubleValue() : 0.0;
        boolean topTier = skill >= 90.0;

        int targetStreak = switch (band) {
            case easy -> (int) Math.round(topStreak * 0.50);
            case medium -> (int) Math.round(topStreak * 0.70);
            case hard -> (int) Math.round(topStreak * 0.90);
            case extreme -> topTier ? topStreak + 1 : topStreak;
        };
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
            return failBuild("no-skill-or-threshold-or-curve");
        Integer userTopStreak = scoreRepository.findMaxStreak115ByUserAndCategoryActive(ctx.userId(), category.getId());
        if (userTopStreak == null || userTopStreak < 3)
            return failBuild("user-streak-too-low");
        BigDecimal streakThreshold = liftedThreshold(ctx, category, skill.getRawApForOneGain());
        MapPick pick = sampleEligibleMap(category, streakThreshold,
                calibrationService.bandMultiplier(template, band), category.getScoreCurve(), rng);
        if (pick == null)
            return failBuild("no-eligible-map");

        Integer mapTopStreak = scoreRepository.findMaxStreak115ByMapDifficulty(pick.difficulty().getId());
        int reference = mapTopStreak != null && mapTopStreak > 0 ? mapTopStreak : userTopStreak;
        double skill1 = skillLevelFor(ctx, category) != null
                ? skillLevelFor(ctx, category).doubleValue()
                : 0.0;
        boolean topTier = skill1 >= 90.0;

        int targetStreak = switch (band) {
            case easy -> (int) Math.round(reference * 0.50);
            case medium -> (int) Math.round(reference * 0.70);
            case hard -> topTier ? reference : (int) Math.round(reference * 0.90);
            case extreme -> topTier ? reference + 1 : reference;
        };

        int userCap = userTopStreak + 2;
        targetStreak = Math.min(targetStreak, userCap);
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
            Integer targetScore, int xpReward, MissionBand effectiveBand) {
    }

    public record MissionPoolCache(List<MissionTemplate> daily, List<MissionTemplate> weekly,
            List<Item> poolableItems,
            ConcurrentHashMap<UUID, BigDecimal> mapWrApByDifficulty) {
    }
}
