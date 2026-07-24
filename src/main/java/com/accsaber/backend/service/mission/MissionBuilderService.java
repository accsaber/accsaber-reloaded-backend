package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.UserCategorySkill;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionBuilderService {

    private static final int SNIPE_CANDIDATE_LIMIT = 50;
    private static final int MAP_SAMPLE_ATTEMPTS = 8;
    private static final int COMPUTE_MAP_RETRIES = 6;
    private static final double STREAK_COMPLEXITY_MIN = 1.0;
    private static final double STREAK_COMPLEXITY_MAX = 15.0;
    private static final double STREAK_COMPLEXITY_BAND_SIZE = 3.0;
    private static final double STREAK_DEFAULT_COMPLEXITY = 7.0;
    private static final double STREAK_COMPLEXITY_BASE_FACTOR = 0.95;
    private static final double STREAK_COMPLEXITY_FACTOR_STEP = 0.15;
    private static final int STREAK_COMPLEXITY_BAND_COUNT = (int) Math
            .ceil((STREAK_COMPLEXITY_MAX - STREAK_COMPLEXITY_MIN) / STREAK_COMPLEXITY_BAND_SIZE);
    private static final BigDecimal STREAK_COMPLEXITY_MIN_DECIMAL = BigDecimal.valueOf(STREAK_COMPLEXITY_MIN);
    private static final BigDecimal STREAK_COMPLEXITY_BAND_SIZE_DECIMAL = BigDecimal.valueOf(STREAK_COMPLEXITY_BAND_SIZE);
    private static final String OVERALL_CODE = "overall";
    private static final ThreadLocal<String> LAST_FAIL_REASON = new ThreadLocal<>();

    private final UserRepository userRepository;
    private final ScoreRepository scoreRepository;
    private final MissionCalibrationService calibrationService;
    private final MissionTargetService targetService;
    private final MissionSkillService skillService;

    public UserMission pickAndBuild(MissionAssignmentContext ctx, List<MissionTemplate> pool,
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
                attempts.add(describeAttempt(template, category));
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Mission slot empty user={} pool={} forcedBand={} reason=all-templates-exhausted attempts={}",
                    ctx.userId(), poolType, forcedBand, attempts);
        }
        return null;
    }

    public UserMission pickAndBuildForCategory(MissionAssignmentContext ctx, List<MissionTemplate> pool,
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
                .filter(t -> forcedBand != MissionBand.hard
                        || (t.getType() != MissionType.PLAY_N_MAPS && t.getType() != MissionType.XP_IN_WINDOW
                                && t.getType() != MissionType.SCORES_N))
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
                tried.add(describeAttempt(template, null));
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Mission slot empty user={} pool={} category={} forcedBand={} reason=all-templates-exhausted attempts={}",
                    ctx.userId(), poolType, category.getCode(), forcedBand, tried);
        }
        return null;
    }

    private UserMission buildMission(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, Random rng, MissionPoolCache cache, MissionBand forcedBand) {
        if (category == null && !typeAllowsNullCategory(template.getType()))
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
            case STREAK_SUM_N, SNIPE_RIVAL_ANY_MAP, AP_GAIN_OVERALL, BATCH_PLAY_N, PB_RANKED_BEFORE_N,
                    CAMPAIGN_COMPLETE_N ->
                failBuild("event-only-type");
            case COMEBACK_PB -> buildComebackPb(ctx, template, category, expiresAt, pool, band, rng, cache);
            case SCORES_N -> buildScoresN(ctx, template, category, expiresAt, pool, band, rng, cache);
        };
    }

    private UserMission buildPlayNMaps(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        MissionBand effectiveBand = rng.nextBoolean() ? MissionBand.easy : MissionBand.medium;
        int count = pickCount(template, effectiveBand, rng);
        int xp = calibrationService.computeXpReward(template, skillService.skillLevelFor(ctx, category),
                effectiveBand, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, effectiveBand)
                .targetCount(count)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildXpInWindow(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        MissionBand effectiveBand = rng.nextBoolean() ? MissionBand.easy : MissionBand.medium;
        BigDecimal multiplier = calibrationService.bandMultiplier(template, effectiveBand);
        BigDecimal rolling = ctx.rollingDailyXp() == null ? BigDecimal.valueOf(500) : ctx.rollingDailyXp();
        int targetXp = rolling.multiply(multiplier).max(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        int xp = Math.max(50, calibrationService.computeXpReward(template,
                skillService.skillLevelFor(ctx, category), effectiveBand, null));
        return baseBuilder(ctx, template, category, expiresAt, pool, effectiveBand)
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
        BigDecimal threshold = skillService.liftedThreshold(ctx, category, skill.getRawApForOneGain());
        BigDecimal pickMultiplier = calibrationService.bandMultiplier(template, band);

        String lastReason = "no-eligible-map";
        for (int attempt = 0; attempt < COMPUTE_MAP_RETRIES; attempt++) {
            MapPick pick = targetService.sampleEligibleMap(category, threshold, pickMultiplier, scoreCurve, rng);
            if (pick == null) {
                lastReason = "no-eligible-map";
                break;
            }
            if (isMapWrBelowFloor(pick, skill, band, cache)) {
                lastReason = "map-wr-below-user-tier";
                continue;
            }
            MissionBand effectiveBand = band;
            Optional<Score> existing = scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                    ctx.userId(), pick.difficulty().getId());
            if (!bandForced && existing.isPresent() && existing.get().getWeightedAp() != null) {
                BigDecimal maxWeightedAp = scoreRepository.findMaxWeightedApByUserAndCategory(
                        ctx.userId(), category.getId());
                MissionBand derived = targetService.bandFromWeightedRatio(existing.get().getWeightedAp(), maxWeightedAp);
                effectiveBand = targetService.blendBands(band, derived);
            }

            BigDecimal effectiveMultiplier = calibrationService.bandMultiplier(template, effectiveBand);
            BigDecimal skillAnchored = calibrationService.targetRawAp(threshold, effectiveMultiplier);
            BigDecimal existingAp = existing.map(Score::getAp).orElse(null);
            BigDecimal liftedFloor = existingAp != null
                    ? calibrationService.bandLiftedFloorAp(existingAp, pick.complexity(), scoreCurve, effectiveBand)
                    : null;
            BigDecimal categorySkill = skillService.skillLevelFor(ctx, category);
            BigDecimal mapTarget = targetService.mapAwareTarget(pick.difficulty().getId(), category.getId(),
                    categorySkill != null ? categorySkill.doubleValue() : 50.0, existingAp, effectiveBand);
            BigDecimal targetRawAp = targetService.blendSkillAndMapTarget(skillAnchored, mapTarget);
            targetRawAp = targetRawAp.max(skillAnchored.multiply(targetService.skillFloorFraction(effectiveBand)));
            if (liftedFloor != null)
                targetRawAp = targetRawAp.max(liftedFloor);
            targetRawAp = targetService.capExtremeAtTopAp(targetRawAp, effectiveBand, skill, categorySkill);
            targetRawAp = targetService.capAtMapRealisticCeiling(targetRawAp, pick, scoreCurve, effectiveBand, cache,
                    categorySkill);
            targetRawAp = targetService.applyLeaderboardDensityDampener(targetRawAp, effectiveBand, pick, cache,
                    existingAp);

            if (existingAp != null && targetRawAp.compareTo(existingAp) <= 0) {
                lastReason = "target-below-existing-after-caps";
                continue;
            }
            BigDecimal minMeaningful = minMeaningfulTarget(effectiveBand, skill, skillAnchored);
            if (targetRawAp.compareTo(minMeaningful) < 0) {
                lastReason = "target-below-min-meaningful";
                continue;
            }

            BigDecimal targetAcc = calibrationService.targetAccuracy(targetRawAp, pick.complexity(), scoreCurve,
                    skill.getTopAp());
            Integer targetScore = scoreFromAcc(targetAcc, pick.maxScore());
            int xp = calibrationService.computeXpReward(template, skillService.skillLevelFor(ctx, category),
                    effectiveBand, null);
            return new MapTargetResult(pick, targetRawAp, targetAcc, targetScore, xp, effectiveBand);
        }
        return failBuild(lastReason);
    }

    private UserMission buildPbSpecificMap(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        if (skill == null || skill.getRawApForOneGain() == null || category.getScoreCurve() == null)
            return failBuild("no-skill-or-threshold-or-curve");
        Curve scoreCurve = category.getScoreCurve();
        BigDecimal threshold = skillService.liftedThreshold(ctx, category, skill.getRawApForOneGain());
        BigDecimal multiplier = calibrationService.bandMultiplier(template, band);
        BigDecimal skillAnchored = calibrationService.targetRawAp(threshold, multiplier);
        BigDecimal categorySkill = skillService.skillLevelFor(ctx, category);

        MapPick pick = null;
        Optional<Score> existing = Optional.empty();
        BigDecimal targetRawAp = null;
        String lastReason = "no-eligible-map";
        for (int attempt = 0; attempt < COMPUTE_MAP_RETRIES; attempt++) {
            MapPick candidate = targetService.sampleEligibleMap(category, threshold, multiplier, scoreCurve, rng);
            if (candidate == null) {
                lastReason = "no-eligible-map";
                break;
            }
            if (isMapWrBelowFloor(candidate, skill, band, cache)) {
                lastReason = "map-wr-below-user-tier";
                continue;
            }
            Optional<Score> myScore = scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                    ctx.userId(), candidate.difficulty().getId());
            BigDecimal existingAp = myScore.map(Score::getAp).orElse(null);
            BigDecimal liftedFloor = existingAp != null
                    ? calibrationService.bandLiftedFloorAp(existingAp, candidate.complexity(), scoreCurve, band)
                    : null;
            BigDecimal mapTarget = targetService.mapAwareTarget(candidate.difficulty().getId(), category.getId(),
                    categorySkill != null ? categorySkill.doubleValue() : 50.0, existingAp, band);
            BigDecimal computed = targetService.blendSkillAndMapTarget(skillAnchored, mapTarget);
            computed = computed.max(skillAnchored.multiply(targetService.skillFloorFraction(band)));
            if (liftedFloor != null)
                computed = computed.max(liftedFloor);
            computed = targetService.capExtremeAtTopAp(computed, band, skill, categorySkill);
            computed = targetService.capAtMapRealisticCeiling(computed, candidate, scoreCurve, band, cache,
                    categorySkill);
            computed = targetService.applyLeaderboardDensityDampener(computed, band, candidate, cache, existingAp);

            if (existingAp != null && computed.compareTo(existingAp) <= 0) {
                lastReason = "target-below-existing-after-caps";
                continue;
            }
            BigDecimal minMeaningful = minMeaningfulTarget(band, skill, skillAnchored);
            if (computed.compareTo(minMeaningful) < 0) {
                lastReason = "target-below-min-meaningful";
                continue;
            }
            pick = candidate;
            existing = myScore;
            targetRawAp = computed;
            break;
        }
        if (pick == null || targetRawAp == null)
            return failBuild(lastReason);

        int xp = calibrationService.computeXpReward(template, skillService.skillLevelFor(ctx, category), band, null);
        xp = (int) Math.round(xp * skillService.pbFreshnessBoost(existing.orElse(null)));
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetMapDifficulty(pick.difficulty())
                .targetAp(targetRawAp)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }


    private BigDecimal pbAboveThresholdAvailabilityCap(List<Score> scores, MissionBand band, BigDecimal threshold) {
        if (band == MissionBand.easy || band == MissionBand.medium || scores.size() < 50) {
            return threshold;
        }
        Score score;
        BigDecimal ap;
        int idx;
        if (scores.size() > 100) {
            double percentile = (band == MissionBand.hard ? 0.15 : 0.10);
            idx = Math.min(scores.size() - 1, Math.max(0, (int) Math.round(scores.size() * percentile)));
        } else {
            idx = (band == MissionBand.hard ? 14 : 9);
        }
        score = scores.get(idx);
        ap = score.getAp().setScale(0, RoundingMode.HALF_UP);
        return (ap.compareTo(threshold) < 0 ? ap : threshold);
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
        BigDecimal categorySkill = skillService.skillLevelFor(ctx, category);
        BigDecimal baseHardCap = topAp.multiply(new BigDecimal("0.97"));
        BigDecimal hardCap = (band == MissionBand.extreme
                ? baseHardCap
                : targetService.applySkillAwareTopApNerf(baseHardCap, categorySkill))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal threshold = rawThreshold.compareTo(hardCap) > 0 ? hardCap : rawThreshold;
        threshold = pbAboveThresholdAvailabilityCap(scores, band, threshold); // new line added
        long qualifying = scores.stream()
                .filter(s -> s.getAp() != null && s.getAp().compareTo(threshold) >= 0)
                .count();
        if (qualifying < 2)
            return failBuild("too-few-qualifying-above-threshold");
        int desiredCount = pickCount(template, band, rng);
        int count = Math.min(desiredCount, (int) qualifying);
        if (count <= 0)
            return failBuild("count-clamp-zero");
        int xp = calibrationService.computeXpReward(template, categorySkill, band, null);
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
        BigDecimal threshold = skillService.liftedThreshold(ctx, category, skill.getRawApForOneGain());
        BigDecimal bandMult = calibrationService.bandMultiplier(template, band);
        BigDecimal categorySkill = skillService.skillLevelFor(ctx, category);
        BigDecimal effectiveUserSkill = skillService.liftedSkillLevel(ctx, category, categorySkill);
        double userSkillVal = effectiveUserSkill != null ? effectiveUserSkill.doubleValue() : 50.0;
        double maxSkillDistance = snipeMaxSkillDistance(band);

        MapPick pick = null;
        Score target = null;
        Optional<Score> mine = Optional.empty();
        for (int attempt = 0; attempt < MAP_SAMPLE_ATTEMPTS; attempt++) {
            MapPick candidate = targetService.sampleEligibleMap(category, threshold, bandMult, scoreCurve, rng);
            if (candidate == null)
                break;
            if (isMapWrBelowFloor(candidate, skill, band, cache))
                continue;
            Optional<Score> myScore = scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                    ctx.userId(), candidate.difficulty().getId());
            int baseline = myScore.map(Score::getScore).orElse(0);
            BigDecimal userCurrentAp = myScore.map(Score::getAp).orElse(null);

            SnipeTarget snipe = computeSnipeTarget(candidate, scoreCurve, threshold, band, skill, userCurrentAp,
                    userSkillVal, category, cache);
            if (snipe == null)
                continue;

            Score picked = pickSnipeCandidate(candidate, ctx.userId(), baseline, category.getId(),
                    snipe, userSkillVal, maxSkillDistance, rng);
            if (picked == null)
                continue;
            pick = candidate;
            target = picked;
            mine = myScore;
            break;
        }
        if (pick == null || target == null)
            return failBuild("no-snipe-candidate-within-band");

        BigDecimal snipeDistance;
        if (mine.isPresent()) {
            BigDecimal effectiveUserAp = skillService.ageAdjustedUserAp(mine.get(), skill.getTopAp());
            snipeDistance = target.getAp().subtract(effectiveUserAp).max(BigDecimal.ZERO);
        } else {
            snipeDistance = bandEquivalentClimb(target.getAp(), pick.complexity(), scoreCurve, band);
        }
        BigDecimal targetAcc = snipeTargetAcc(target, pick.maxScore());
        int xp = calibrationService.computeXpReward(template, skillService.skillLevelFor(ctx, category), band,
                snipeDistance);
        return baseBuilder(ctx, template, category, expiresAt, pool, band)
                .targetMapDifficulty(pick.difficulty())
                .targetPlayer(target.getUser())
                .targetScore(target.getScore())
                .targetAp(target.getAp())
                .targetAcc(targetAcc)
                .snipeDistance(snipeDistance)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private SnipeTarget computeSnipeTarget(MapPick candidate, Curve scoreCurve, BigDecimal threshold,
            MissionBand band, UserCategorySkill skill, BigDecimal userCurrentAp, double userSkillVal,
            Category category, MissionPoolCache cache) {
        BigDecimal skillLevel = BigDecimal.valueOf(userSkillVal);
        BigDecimal skillAnchored = threshold.multiply(targetService.snipeBandFraction(band));
        BigDecimal skillFloor = skillAnchored.multiply(targetService.skillFloorFraction(band));
        if (userCurrentAp != null && userCurrentAp.signum() > 0) {
            BigDecimal lifted = calibrationService.bandLiftedFloorAp(userCurrentAp, candidate.complexity(),
                    scoreCurve, band);
            BigDecimal capped = lifted.max(skillFloor);
            capped = targetService.capExtremeAtTopAp(capped, band, skill, skillLevel);
            capped = targetService.applyLeaderboardDensityDampener(capped, band, candidate, cache, userCurrentAp);
            if (capped.compareTo(userCurrentAp) <= 0)
                return null;
            return new SnipeTarget(capped, capped, userCurrentAp);
        }
        BigDecimal mapTarget = targetService.mapAwareTarget(candidate.difficulty().getId(), category.getId(),
                userSkillVal, null, band);
        BigDecimal target = targetService.blendSkillAndMapTarget(skillAnchored, mapTarget);
        target = target.max(skillFloor);
        target = targetService.capExtremeAtTopAp(target, band, skill, skillLevel);
        target = targetService.applyLeaderboardDensityDampener(target, band, candidate, cache, null);
        if (target == null)
            return null;
        BigDecimal apCap = targetService.capExtremeAtTopAp(target.multiply(snipeSlack(band)), band, skill, skillLevel);
        return new SnipeTarget(target, apCap, snipeMinAp(target, band));
    }

    private BigDecimal bandEquivalentClimb(BigDecimal targetAp, BigDecimal complexity, Curve scoreCurve,
            MissionBand band) {
        BigDecimal lifted = calibrationService.bandLiftedFloorAp(targetAp, complexity, scoreCurve, band);
        if (lifted == null)
            return BigDecimal.ZERO;
        return lifted.subtract(targetAp).max(BigDecimal.ZERO);
    }

    private BigDecimal snipeSlack(MissionBand band) {
        return switch (band) {
            case easy -> new BigDecimal("1.00");
            case medium -> new BigDecimal("1.01");
            case hard -> new BigDecimal("1.03");
            case extreme -> new BigDecimal("1.04");
        };
    }

    private BigDecimal snipeMinAp(BigDecimal targetAp, MissionBand band) {
        return targetAp.multiply(snipeFloorFraction(band));
    }

    private BigDecimal snipeFloorFraction(MissionBand band) {
        return switch (band) {
            case easy -> new BigDecimal("0.95");
            case medium -> new BigDecimal("0.97");
            case hard -> new BigDecimal("0.99");
            case extreme -> new BigDecimal("1.01");
        };
    }

    private Score pickSnipeCandidate(MapPick pick, Long userId, int baseline, UUID categoryId,
            SnipeTarget snipe, double userSkillVal, double maxSkillDistance, Random rng) {
        List<Object[]> rows = scoreRepository.findSnipeCandidatesAboveBaselineWithSkill(
                pick.difficulty().getId(), userId, baseline, categoryId, snipe.targetAp(),
                PageRequest.of(0, SNIPE_CANDIDATE_LIMIT * 2));
        List<Score> viable = rows.stream()
                .filter(row -> Math.abs(((BigDecimal) row[1]).doubleValue() - userSkillVal) <= maxSkillDistance)
                .filter(row -> {
                    Score s = (Score) row[0];
                    return snipe.apCap() == null || s.getAp() == null || s.getAp().compareTo(snipe.apCap()) <= 0;
                })
                .filter(row -> {
                    Score s = (Score) row[0];
                    return s.getAp() != null && s.getAp().compareTo(snipe.minAp()) >= 0;
                })
                .sorted(Comparator.comparing(row -> ((Score) row[0]).getAp().subtract(snipe.targetAp()).abs()))
                .map(row -> (Score) row[0])
                .toList();
        if (viable.isEmpty())
            return null;
        return viable.get(rng.nextInt(Math.min(3, viable.size())));
    }

    private double snipeMaxSkillDistance(MissionBand band) {
        return switch (band) {
            case easy -> 5.0;
            case medium -> 8.0;
            case hard -> 12.0;
            case extreme -> 18.0;
        };
    }

    private BigDecimal snipeTargetAcc(Score target, Integer maxScore) {
        if (maxScore == null || maxScore <= 0 || target.getScoreNoMods() == null)
            return null;
        return BigDecimal.valueOf(target.getScoreNoMods())
                .divide(BigDecimal.valueOf(maxScore), 10, RoundingMode.HALF_UP);
    }

    private UserMission buildScoresN(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        MissionBand effectiveBand = rng.nextBoolean() ? MissionBand.easy : MissionBand.medium;
        int min = template.getTargetCountMin() != null ? template.getTargetCountMin() : 1;
        int max = template.getTargetCountMax() != null ? template.getTargetCountMax() : 3;
        int count = min + rng.nextInt(Math.max(1, max - min + 1));
        int xp = calibrationService.computeXpReward(template, skillService.skillLevelFor(ctx, category),
                effectiveBand, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, effectiveBand)
                .targetCount(count)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildComebackPb(MissionAssignmentContext ctx, MissionTemplate template, Category category,
            Instant expiresAt, MissionPool pool, MissionBand band, Random rng, MissionPoolCache cache) {
        Instant olderThan = Instant.now().minus(Duration.ofDays(365));
        List<Score> oldScores = scoreRepository.findActiveByUserAndCategoryOlderThan(
                ctx.userId(), category.getId(), olderThan);
        if (oldScores.isEmpty())
            return failBuild("no-old-scores-for-comeback");
        Score chosen = oldScores.get(rng.nextInt(oldScores.size()));
        BigDecimal maxWeightedAp = scoreRepository.findMaxWeightedApByUserAndCategory(
                ctx.userId(), category.getId());
        MissionBand effectiveBand = targetService.bandFromWeightedRatio(chosen.getWeightedAp(), maxWeightedAp);
        BigDecimal bandMult = calibrationService.bandMultiplier(template, effectiveBand);
        BigDecimal targetRawAp = chosen.getAp().multiply(bandMult).max(chosen.getAp().add(BigDecimal.ONE));
        UserCategorySkill skill = ctx.skillByCategoryId().get(category.getId());
        BigDecimal categorySkill = skillService.skillLevelFor(ctx, category);
        if (skill != null)
            targetRawAp = targetService.capExtremeAtTopAp(targetRawAp, effectiveBand, skill, categorySkill);
        MapPick pick = new MapPick(chosen.getMapDifficulty(), null, chosen.getMapDifficulty().getMaxScore());
        targetRawAp = targetService.capAtMapRealisticCeiling(targetRawAp, pick, category.getScoreCurve(),
                effectiveBand, cache, categorySkill);
        targetRawAp = targetService.applyLeaderboardDensityDampener(targetRawAp, effectiveBand, pick, cache,
                chosen.getAp());
        int xp = calibrationService.computeXpReward(template, categorySkill, effectiveBand, null);
        return baseBuilder(ctx, template, category, expiresAt, pool, effectiveBand)
                .targetMapDifficulty(chosen.getMapDifficulty())
                .targetAp(targetRawAp)
                .xpReward(xp)
                .itemReward(rollItemReward(template, rng, cache))
                .build();
    }

    private UserMission buildStreakNInCategory(MissionAssignmentContext ctx, MissionTemplate template,
            Category category, Instant expiresAt, MissionPool pool, MissionBand band, Random rng,
            MissionPoolCache cache) {
        int reference = skillService.representativeUserStreak(ctx.userId(), category.getId(), band);
        if (reference < 3)
            return failBuild("user-streak-too-low");
        BigDecimal skillLevel = skillService.skillLevelFor(ctx, category);
        boolean topTier = skillLevel != null && skillLevel.doubleValue() >= 90.0;
        int targetStreak = Math.max(3, band == MissionBand.extreme && topTier ? reference + 1 : reference);
        int count = pickCount(template, band, rng);
        int xp = calibrationService.computeXpReward(template, skillLevel, band, null);
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
        BigDecimal streakThreshold = skillService.liftedThreshold(ctx, category, skill.getRawApForOneGain());
        BigDecimal skillLvl = skillService.skillLevelFor(ctx, category);
        boolean topTier = skillLvl != null && skillLvl.doubleValue() >= 90.0;
        String lastReason = "no-eligible-map";
        for (int attempt = 0; attempt < COMPUTE_MAP_RETRIES; attempt++) {
            MapPick pick = targetService.sampleEligibleMap(category, streakThreshold,
                    calibrationService.bandMultiplier(template, band), category.getScoreCurve(), rng);
            if (pick == null) {
                lastReason = "no-eligible-map";
                continue;
            }
            int bandAbility = complexityBandAbility(ctx.userId(), category.getId(), band, pick);
            if (bandAbility < 3) {
                lastReason = "user-streak-too-low-for-complexity";
                continue;
            }
            int reference = mapStreakReference(pick, bandAbility);
            int targetStreak = Math.max(3, Math.min(streakTargetFor(band, reference, topTier), bandAbility + 2));
            int xp = calibrationService.computeXpReward(template, skillLvl, band, null);
            return baseBuilder(ctx, template, category, expiresAt, pool, band)
                    .targetMapDifficulty(pick.difficulty())
                    .targetStreak(targetStreak)
                    .xpReward(xp)
                    .itemReward(rollItemReward(template, rng, cache))
                    .build();
        }
        return failBuild(lastReason);
    }

    private int complexityBandAbility(Long userId, UUID categoryId, MissionBand band, MapPick pick) {
        BigDecimal[] complexityBand = streakComplexityBand(pick.complexity());
        MissionSkillService.RepresentativeStreak rep = skillService.representativeUserStreakForComplexityBand(
                userId, categoryId, band, complexityBand[0], complexityBand[1]);
        if (rep.fromComplexityBand())
            return rep.value();
        return (int) Math.round(rep.value() * complexityTranslationFactor(pick.complexity()));
    }

    private int mapStreakReference(MapPick pick, int bandAbility) {
        List<Integer> topStreaks = scoreRepository.findTopStreak115ValuesByMapDifficulty(
                pick.difficulty().getId(), PageRequest.of(0, 5));
        if (topStreaks.isEmpty())
            return bandAbility;
        int max = topStreaks.get(0);
        double avg = topStreaks.stream().mapToInt(Integer::intValue).average().orElse(max);
        return Math.max(2, (int) Math.round(0.6 * avg + 0.4 * max));
    }

    private int complexityBandIndex(double complexity) {
        double clamped = Math.max(STREAK_COMPLEXITY_MIN, Math.min(STREAK_COMPLEXITY_MAX, complexity));
        int index = (int) Math.floor((clamped - STREAK_COMPLEXITY_MIN) / STREAK_COMPLEXITY_BAND_SIZE);
        return Math.max(0, Math.min(index, STREAK_COMPLEXITY_BAND_COUNT - 1));
    }

    private double complexityTranslationFactor(BigDecimal complexity) {
        double raw = complexity != null ? complexity.doubleValue() : STREAK_DEFAULT_COMPLEXITY;
        return STREAK_COMPLEXITY_BASE_FACTOR - complexityBandIndex(raw) * STREAK_COMPLEXITY_FACTOR_STEP;
    }

    private BigDecimal[] streakComplexityBand(BigDecimal complexity) {
        double raw = complexity != null ? complexity.doubleValue() : STREAK_DEFAULT_COMPLEXITY;
        int bandIndex = complexityBandIndex(raw);
        BigDecimal lo = STREAK_COMPLEXITY_MIN_DECIMAL
                .add(STREAK_COMPLEXITY_BAND_SIZE_DECIMAL.multiply(BigDecimal.valueOf(bandIndex)));
        BigDecimal hiExclusive = lo.add(STREAK_COMPLEXITY_BAND_SIZE_DECIMAL);
        return new BigDecimal[] { lo, hiExclusive };
    }

    private int streakTargetFor(MissionBand band, int reference, boolean topTier) {
        return switch (band) {
            case easy -> (int) Math.round(reference * 0.50);
            case medium -> (int) Math.round(reference * 0.70);
            case hard -> topTier ? reference : (int) Math.round(reference * 0.90);
            case extreme -> topTier ? reference + 1 : reference;
        };
    }

    private boolean isMapWrBelowFloor(MapPick pick, UserCategorySkill skill, MissionBand band,
            MissionPoolCache cache) {
        if (skill.getTopAp() == null || skill.getTopAp().signum() <= 0)
            return false;
        BigDecimal wr = targetService.resolveMapWr(pick, cache);
        if (wr.signum() <= 0)
            return false;
        return wr.compareTo(skill.getTopAp().multiply(targetService.mapWrFloorForBand(band))) < 0;
    }

    private BigDecimal minMeaningfulTarget(MissionBand band, UserCategorySkill skill, BigDecimal skillAnchored) {
        boolean highBand = band == MissionBand.hard || band == MissionBand.extreme;
        if (highBand && skill.getTopAp() != null)
            return skill.getTopAp().multiply(new BigDecimal("0.70"));
        return skillAnchored.multiply(new BigDecimal("0.80"));
    }

    private Integer scoreFromAcc(BigDecimal targetAcc, Integer maxScore) {
        if (maxScore == null)
            return null;
        return BigDecimal.valueOf(maxScore).multiply(targetAcc)
                .setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private Category pickCategoryForType(MissionAssignmentContext ctx, MissionTemplate template, Random rng,
            Set<UUID> exclude) {
        if (template.getType() == MissionType.XP_IN_WINDOW || template.getType() == MissionType.SCORES_N)
            return null;
        if (template.getType() == MissionType.PLAY_N_MAPS && template.getCode() != null
                && template.getCode().endsWith("_any"))
            return null;
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

    private boolean requiresMapPick(MissionType type) {
        return switch (type) {
            case ACC_ON_MAP, AP_ON_MAP, PB_SPECIFIC_MAP, SNIPE_PLAYER_ON_MAP, STREAK_ON_MAP, COMEBACK_PB -> true;
            default -> false;
        };
    }

    private boolean typeAllowsNullCategory(MissionType type) {
        return type == MissionType.XP_IN_WINDOW || type == MissionType.PLAY_N_MAPS || type == MissionType.SCORES_N;
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
        if (template.getAwardsItem() != null)
            return template.getAwardsItem();
        if (cache.eventCrate() != null && rng.nextInt(100) < 20)
            return cache.eventCrate();
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

    private String describeAttempt(MissionTemplate template, Category category) {
        String reason = LAST_FAIL_REASON.get();
        String suffix = category != null ? "/" + category.getCode() : "/-";
        return template.getCode() + suffix + ":" + (reason != null ? reason : "no-reason");
    }

    private static <T> T failBuild(String reason) {
        LAST_FAIL_REASON.set(reason);
        return null;
    }

    private record SnipeTarget(BigDecimal targetAp, BigDecimal apCap, BigDecimal minAp) {
    }

    private record MapTargetResult(MapPick pick, BigDecimal targetRawAp, BigDecimal targetAcc,
            Integer targetScore, int xpReward, MissionBand effectiveBand) {
    }
}
