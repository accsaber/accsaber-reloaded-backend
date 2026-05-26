package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.UserCategorySkill;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionBuilderService {

    private static final int SNIPE_CANDIDATE_LIMIT = 50;
    private static final String OVERALL_CODE = "overall";
    private static final ThreadLocal<String> LAST_FAIL_REASON = new ThreadLocal<>();

    private static <T> T failBuild(String reason) {
        LAST_FAIL_REASON.set(reason);
        return null;
    }

    private final UserRepository userRepository;
    private final ScoreRepository scoreRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MissionCalibrationService calibrationService;

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
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
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
        if (template.getType() == MissionType.XP_IN_WINDOW)
            return null;
        if (template.getType() == MissionType.SCORES_N)
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
        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, null);
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

        String lastReason = "no-eligible-map";
        for (int attempt = 0; attempt < 6; attempt++) {
            MapPick pick = sampleEligibleMap(category, threshold, pickMultiplier, scoreCurve, rng);
            if (pick == null) {
                lastReason = "no-eligible-map";
                break;
            }
            if (skill.getTopAp() != null && skill.getTopAp().signum() > 0) {
                BigDecimal mapWr = cache.mapWrApByDifficulty().computeIfAbsent(pick.difficulty().getId(), id -> {
                    BigDecimal val = scoreRepository.findMaxApByMapDifficulty(id);
                    return val != null ? val : BigDecimal.ZERO;
                });
                if (mapWr.signum() > 0
                        && mapWr.compareTo(skill.getTopAp().multiply(mapWrFloorForBand(band))) < 0) {
                    lastReason = "map-wr-below-user-tier";
                    continue;
                }
            }
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
            BigDecimal categorySkill = skillLevelFor(ctx, category);
            BigDecimal mapTarget = mapAwareTarget(pick.difficulty().getId(), category.getId(),
                    categorySkill != null ? categorySkill.doubleValue() : 50.0, existingAp, effectiveBand);
            BigDecimal targetRawAp = blendSkillAndMapTarget(skillAnchored, mapTarget);
            if (liftedFloor != null)
                targetRawAp = targetRawAp.max(liftedFloor);
            targetRawAp = capExtremeAtTopAp(targetRawAp, effectiveBand, skill);
            targetRawAp = capAtMapRealisticCeiling(targetRawAp, pick, scoreCurve, effectiveBand, cache,
                    categorySkill);

            if (existingAp != null && targetRawAp.compareTo(existingAp) <= 0) {
                lastReason = "target-below-existing-after-caps";
                continue;
            }
            BigDecimal minMeaningful = (effectiveBand == MissionBand.hard || effectiveBand == MissionBand.extreme)
                    && skill.getTopAp() != null
                    ? skill.getTopAp().multiply(new BigDecimal("0.80"))
                    : skillAnchored.multiply(new BigDecimal("0.85"));
            if (targetRawAp.compareTo(minMeaningful) < 0) {
                lastReason = "target-below-min-meaningful";
                continue;
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
        return failBuild(lastReason);
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
        BigDecimal categorySkill = skillLevelFor(ctx, category);
        BigDecimal mapTarget = mapAwareTarget(pick.difficulty().getId(), category.getId(),
                categorySkill != null ? categorySkill.doubleValue() : 50.0, existingAp, band);
        BigDecimal targetRawAp = blendSkillAndMapTarget(skillAnchored, mapTarget);
        if (liftedFloor != null)
            targetRawAp = targetRawAp.max(liftedFloor);
        targetRawAp = capExtremeAtTopAp(targetRawAp, band, skill);
        targetRawAp = capAtMapRealisticCeiling(targetRawAp, pick, scoreCurve, band, cache, categorySkill);

        if (existingAp != null && targetRawAp.compareTo(existingAp) <= 0)
            return failBuild("target-below-existing-after-caps");
        BigDecimal minMeaningful = (band == MissionBand.hard || band == MissionBand.extreme)
                && skill.getTopAp() != null
                ? skill.getTopAp().multiply(new BigDecimal("0.70"))
                : skillAnchored.multiply(new BigDecimal("0.80"));
        if (existingAp == null && targetRawAp.compareTo(minMeaningful) < 0)
            return failBuild("target-below-min-meaningful");

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

        MapPick pick = null;
        Score target = null;
        Optional<Score> mine = Optional.empty();
        for (int attempt = 0; attempt < 8; attempt++) {
            MapPick candidate = sampleEligibleMap(category, threshold, bandMult, scoreCurve, rng);
            if (candidate == null)
                break;
            if (skill.getTopAp() != null && skill.getTopAp().signum() > 0) {
                BigDecimal mapWr = cache.mapWrApByDifficulty().computeIfAbsent(candidate.difficulty().getId(),
                        id -> {
                            BigDecimal val = scoreRepository.findMaxApByMapDifficulty(id);
                            return val != null ? val : BigDecimal.ZERO;
                        });
                if (mapWr.signum() > 0
                        && mapWr.compareTo(skill.getTopAp().multiply(mapWrFloorForBand(band))) < 0) {
                    continue;
                }
            }
            Optional<Score> myScore = scoreRepository.findByUser_IdAndMapDifficulty_IdAndActiveTrue(
                    ctx.userId(), candidate.difficulty().getId());
            int baseline = myScore.map(Score::getScore).orElse(0);
            BigDecimal userCurrentAp = myScore.map(Score::getAp).orElse(null);

            BigDecimal targetAp;
            BigDecimal apCap;
            if (userCurrentAp != null && userCurrentAp.signum() > 0) {
                targetAp = calibrationService.bandLiftedFloorAp(userCurrentAp, candidate.complexity(),
                        scoreCurve, band);
                apCap = targetAp;
            } else {
                BigDecimal snipeBandFraction = switch (band) {
                    case easy -> new BigDecimal("0.90");
                    case medium -> new BigDecimal("0.95");
                    case hard -> new BigDecimal("0.98");
                    case extreme -> new BigDecimal("1.02");
                };
                BigDecimal skillAnchoredSnipe = threshold.multiply(snipeBandFraction);
                BigDecimal mapTarget = mapAwareTarget(candidate.difficulty().getId(), category.getId(),
                        userSkillVal, null, band);
                targetAp = blendSkillAndMapTarget(skillAnchoredSnipe, mapTarget);
                targetAp = capExtremeAtTopAp(targetAp, band, skill);
                BigDecimal candidateApSlack = switch (band) {
                    case easy -> new BigDecimal("1.00");
                    case medium -> new BigDecimal("1.01");
                    case hard -> new BigDecimal("1.03");
                    case extreme -> new BigDecimal("1.04");
                };
                apCap = targetAp.multiply(candidateApSlack);
            }
            if (targetAp == null)
                continue;

            List<Object[]> candidateRows = scoreRepository.findSnipeCandidatesAboveBaselineWithSkill(
                    candidate.difficulty().getId(), ctx.userId(), baseline, category.getId(),
                    PageRequest.of(0, SNIPE_CANDIDATE_LIMIT * 2));

            final BigDecimal targetApFinal = targetAp;
            final BigDecimal apCapFinal = apCap;
            List<Score> viable = candidateRows.stream()
                    .filter(row -> {
                        BigDecimal cSkill = (BigDecimal) row[1];
                        return Math.abs(cSkill.doubleValue() - userSkillVal) <= maxSkillDistance;
                    })
                    .filter(row -> {
                        Score s = (Score) row[0];
                        if (apCapFinal == null || s.getAp() == null)
                            return true;
                        return s.getAp().compareTo(apCapFinal) <= 0;
                    })
                    .sorted(Comparator.comparing(row -> {
                        Score s = (Score) row[0];
                        return s.getAp().subtract(targetApFinal).abs();
                    }))
                    .map(row -> (Score) row[0])
                    .toList();
            if (viable.isEmpty())
                continue;

            int jitter = Math.min(3, viable.size());
            pick = candidate;
            target = viable.get(rng.nextInt(jitter));
            mine = myScore;
            break;
        }
        if (pick == null || target == null)
            return failBuild("no-snipe-candidate-within-band");

        BigDecimal effectiveUserAp = mine.map(s -> ageAdjustedUserAp(s, skill.getTopAp()))
                .orElse(BigDecimal.ZERO);
        BigDecimal snipeDistance = target.getAp().subtract(effectiveUserAp).max(BigDecimal.ZERO);

        BigDecimal targetAcc = null;
        if (pick.maxScore() != null && pick.maxScore() > 0 && target.getScoreNoMods() != null) {
            targetAcc = BigDecimal.valueOf(target.getScoreNoMods())
                    .divide(BigDecimal.valueOf(pick.maxScore()), 10, RoundingMode.HALF_UP);
        }

        int xp = calibrationService.computeXpReward(template, skillLevelFor(ctx, category), band, snipeDistance);
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
        if (skill != null)
            targetRawAp = capExtremeAtTopAp(targetRawAp, effectiveBand, skill);
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

    private UserMission buildStreakNInCategory(MissionAssignmentContext ctx, MissionTemplate template,
            Category category, Instant expiresAt, MissionPool pool, MissionBand band, Random rng,
            MissionPoolCache cache) {
        int reference = representativeUserStreak(ctx.userId(), category.getId(), band);
        if (reference < 3)
            return failBuild("user-streak-too-low");
        BigDecimal skillLevel = skillLevelFor(ctx, category);
        double skill = skillLevel != null ? skillLevel.doubleValue() : 0.0;
        boolean topTier = skill >= 90.0;

        int targetStreak = band == MissionBand.extreme && topTier ? reference + 1 : reference;
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
        int userRepresentativeStreak = representativeUserStreak(ctx.userId(), category.getId(), band);
        if (userRepresentativeStreak < 3)
            return failBuild("user-streak-too-low");
        BigDecimal streakThreshold = liftedThreshold(ctx, category, skill.getRawApForOneGain());
        MapPick pick = sampleEligibleMap(category, streakThreshold,
                calibrationService.bandMultiplier(template, band), category.getScoreCurve(), rng);
        if (pick == null)
            return failBuild("no-eligible-map");

        Integer mapTopStreak = scoreRepository.findMaxStreak115ByMapDifficulty(pick.difficulty().getId());
        int reference;
        if (mapTopStreak != null && mapTopStreak > 0) {
            reference = mapTopStreak;
        } else {
            double complexity = pick.complexity() != null ? pick.complexity().doubleValue() : 7.0;
            double complexityFactor;
            if (complexity >= 10.0)
                complexityFactor = 0.35;
            else if (complexity >= 8.0)
                complexityFactor = 0.55;
            else if (complexity >= 6.0)
                complexityFactor = 0.75;
            else
                complexityFactor = 0.95;
            reference = Math.max(2, (int) Math.round(userRepresentativeStreak * complexityFactor));
        }
        BigDecimal skillLvl = skillLevelFor(ctx, category);
        boolean topTier = (skillLvl != null ? skillLvl.doubleValue() : 0.0) >= 90.0;

        int targetStreak = switch (band) {
            case easy -> (int) Math.round(reference * 0.50);
            case medium -> (int) Math.round(reference * 0.70);
            case hard -> topTier ? reference : (int) Math.round(reference * 0.90);
            case extreme -> topTier ? reference + 1 : reference;
        };

        int userCap = userRepresentativeStreak + 2;
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
        if (template.getAwardsItem() != null)
            return template.getAwardsItem();
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
        if (categoryThreshold == null || targetCategory == null)
            return categoryThreshold;
        UserCategorySkill targetSkill = ctx.skillByCategoryId().get(targetCategory.getId());
        BigDecimal targetSkillLevel = targetSkill != null && targetSkill.getSkillLevel() != null
                ? targetSkill.getSkillLevel()
                : BigDecimal.ZERO;

        UserCategorySkill bestOther = ctx.skillByCategoryId().values().stream()
                .filter(s -> s.getCategory() != null)
                .filter(s -> !OVERALL_CODE.equals(s.getCategory().getCode()))
                .filter(s -> !s.getCategory().getId().equals(targetCategory.getId()))
                .filter(s -> s.getRawApForOneGain() != null && s.getSkillLevel() != null)
                .max(Comparator.comparing(UserCategorySkill::getSkillLevel))
                .orElse(null);
        if (bestOther == null)
            return categoryThreshold;
        BigDecimal skillGap = bestOther.getSkillLevel().subtract(targetSkillLevel);
        if (skillGap.compareTo(new BigDecimal("10")) < 0)
            return categoryThreshold;
        BigDecimal bestThreshold = bestOther.getRawApForOneGain();
        if (bestThreshold.compareTo(categoryThreshold) <= 0)
            return categoryThreshold;

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
            if (playRatio >= 1.0)
                return categoryThreshold;
            double playDampen = Math.max(0.30, 1.0 - playRatio * 0.7);
            liftFraction = liftFraction.multiply(BigDecimal.valueOf(playDampen));
        }
        BigDecimal gap = bestThreshold.subtract(categoryThreshold);
        return categoryThreshold.add(gap.multiply(liftFraction));
    }

    private BigDecimal liftedSkillLevel(MissionAssignmentContext ctx, Category targetCategory,
            BigDecimal categorySkillLevel) {
        if (categorySkillLevel == null || targetCategory == null)
            return categorySkillLevel;
        UserCategorySkill bestOther = ctx.skillByCategoryId().values().stream()
                .filter(s -> s.getCategory() != null)
                .filter(s -> !OVERALL_CODE.equals(s.getCategory().getCode()))
                .filter(s -> !s.getCategory().getId().equals(targetCategory.getId()))
                .filter(s -> s.getSkillLevel() != null)
                .max(Comparator.comparing(UserCategorySkill::getSkillLevel))
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

    private BigDecimal capExtremeAtTopAp(BigDecimal targetRawAp, MissionBand band, UserCategorySkill skill) {
        if (skill.getTopAp() == null || skill.getTopAp().signum() <= 0)
            return targetRawAp;
        double skillVal = skill.getSkillLevel() != null ? skill.getSkillLevel().doubleValue() : 50.0;
        double skillAdj = Math.max(0.0, Math.min(1.0, (skillVal - 50.0) / 50.0));
        double factor = switch (band) {
            case easy -> 0.89 + skillAdj * 0.06;
            case medium -> 0.91 + skillAdj * 0.07;
            case hard -> 0.93 + skillAdj * 0.08;
            case extreme -> 0.91 + skillAdj * 0.11;
        };
        return targetRawAp.min(skill.getTopAp().multiply(BigDecimal.valueOf(factor)));
    }

    private int representativeUserStreak(Long userId, java.util.UUID categoryId, MissionBand band) {
        List<Integer> top = scoreRepository.findTopStreak115ValuesByUserAndCategory(
                userId, categoryId, PageRequest.of(0, 10));
        if (top.isEmpty())
            return 0;
        int max = top.get(0);
        int effectiveTop;
        if (top.size() >= 4 && max > top.get(3) * 1.5) {
            effectiveTop = top.get(3);
        } else if (top.size() >= 2 && max > top.get(1) * 1.5) {
            effectiveTop = top.get(1);
        } else {
            effectiveTop = max;
        }
        return switch (band) {
            case easy -> top.get(Math.min(5, top.size() - 1));
            case medium -> top.get(Math.min(3, top.size() - 1));
            case hard -> top.get(Math.min(1, top.size() - 1));
            case extreme -> effectiveTop;
        };
    }

    private BigDecimal mapWrFloorForBand(MissionBand band) {
        return switch (band) {
            case easy -> new BigDecimal("0.80");
            case medium -> new BigDecimal("0.86");
            case hard -> new BigDecimal("0.90");
            case extreme -> new BigDecimal("0.94");
        };
    }

    private BigDecimal blendSkillAndMapTarget(BigDecimal skillAnchored, BigDecimal mapTarget) {
        if (mapTarget == null)
            return skillAnchored;
        if (skillAnchored == null)
            return mapTarget;
        BigDecimal mapWeighted = mapTarget.multiply(new BigDecimal("0.70"));
        BigDecimal skillWeighted = skillAnchored.multiply(new BigDecimal("0.30"));
        return mapWeighted.add(skillWeighted);
    }

    private BigDecimal mapAwareTarget(java.util.UUID mapDifficultyId, java.util.UUID categoryId,
            double userSkill, BigDecimal userExistingAp, MissionBand band) {
        List<Object[]> rows = scoreRepository.findLeaderboardApAndSkill(mapDifficultyId, categoryId);
        if (rows.isEmpty())
            return null;
        int size = rows.size();
        int naturalIdx = size;
        if (userExistingAp != null && userExistingAp.signum() > 0) {
            for (int i = 0; i < size; i++) {
                BigDecimal candidateAp = (BigDecimal) rows.get(i)[0];
                if (candidateAp.compareTo(userExistingAp) <= 0) {
                    naturalIdx = i;
                    break;
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                BigDecimal candidateSkill = (BigDecimal) rows.get(i)[1];
                if (candidateSkill.doubleValue() <= userSkill) {
                    naturalIdx = i;
                    break;
                }
            }
        }
        int rankShift = switch (band) {
            case easy -> Math.max(1, (int) Math.round(naturalIdx * 0.10));
            case medium -> 0;
            case hard -> -Math.max(2, (int) Math.round(naturalIdx * 0.30));
            case extreme -> -Math.max(3, (int) Math.round(naturalIdx * 0.50));
        };
        int targetIdx = Math.max(0, Math.min(size - 1, naturalIdx + rankShift));
        return (BigDecimal) rows.get(targetIdx)[0];
    }

    private BigDecimal capAtMapRealisticCeiling(BigDecimal targetRawAp, MapPick pick, Curve scoreCurve,
            MissionBand band, MissionPoolCache cache, BigDecimal skillLevel) {
        BigDecimal ceilingFraction = skillAwareBandFraction(band, skillLevel);
        BigDecimal wr = cache.mapWrApByDifficulty().computeIfAbsent(pick.difficulty().getId(), id -> {
            BigDecimal val = scoreRepository.findMaxApByMapDifficulty(id);
            return val != null ? val : BigDecimal.ZERO;
        });
        if (wr.signum() > 0)
            return targetRawAp.min(wr.multiply(ceilingFraction));
        BigDecimal fallback = calibrationService.maxRealisticRawAp(pick.complexity(), scoreCurve);
        if (fallback == null || fallback.signum() <= 0)
            return targetRawAp;
        return targetRawAp.min(fallback.multiply(ceilingFraction));
    }

    private BigDecimal skillAwareBandFraction(MissionBand band, BigDecimal skillLevel) {
        double skill = skillLevel != null ? Math.min(100.0, Math.max(0.0, skillLevel.doubleValue())) : 50.0;
        double skillAdj = Math.max(0.0, (skill - 50.0) / 50.0);
        double frac = switch (band) {
            case easy -> 0.75 + skillAdj * 0.10;
            case medium -> 0.82 + skillAdj * 0.10;
            case hard -> 0.88 + skillAdj * 0.08;
            case extreme -> 0.94 + skillAdj * 0.08;
        };
        return BigDecimal.valueOf(frac);
    }

    private MissionBand bandFromWeightedRatio(BigDecimal weighted, BigDecimal maxWeighted) {
        if (weighted == null || maxWeighted == null || maxWeighted.signum() <= 0)
            return MissionBand.medium;
        double ratio = weighted.doubleValue() / maxWeighted.doubleValue();
        if (ratio >= 0.80)
            return MissionBand.extreme;
        if (ratio >= 0.40)
            return MissionBand.hard;
        if (ratio >= 0.10)
            return MissionBand.medium;
        return MissionBand.easy;
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

    private BigDecimal ageAdjustedUserAp(Score myScore, BigDecimal topAp) {
        BigDecimal scoreAp = myScore.getAp() != null ? myScore.getAp() : BigDecimal.ZERO;
        Instant when = myScore.getTimeSet() != null ? myScore.getTimeSet() : myScore.getCreatedAt();
        if (when == null || topAp == null || topAp.compareTo(scoreAp) <= 0)
            return scoreAp;
        long days = java.time.Duration.between(when, Instant.now()).toDays();
        if (days <= 0)
            return scoreAp;
        double agingFactor = Math.max(0.0, Math.min(1.0, (365.0 - days) / 365.0));
        double liftWeight = (1.0 - agingFactor) * 0.20;
        if (liftWeight <= 0)
            return scoreAp;
        BigDecimal lift = topAp.subtract(scoreAp).multiply(BigDecimal.valueOf(liftWeight));
        return scoreAp.add(lift);
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

    private record MapPick(MapDifficulty difficulty, BigDecimal complexity, Integer maxScore) {
    }

    private record MapTargetResult(MapPick pick, BigDecimal targetRawAp, BigDecimal targetAcc,
            Integer targetScore, int xpReward, MissionBand effectiveBand) {
    }
}
