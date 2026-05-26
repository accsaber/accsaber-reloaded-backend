package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategorySkill;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserCategorySkillRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionAssignmentService {

    private static final int DAILY_MISSION_COUNT = 2;
    private static final String OVERALL_CODE = "overall";

    private final UserRepository userRepository;
    private final UserCategorySkillRepository skillRepository;
    private final UserCategoryStatisticsRepository statsRepository;
    private final ScoreRepository scoreRepository;
    private final MissionTemplateRepository templateRepository;
    private final UserMissionRepository userMissionRepository;
    private final ItemRepository itemRepository;
    private final MissionBuilderService builderService;
    private final MissionRolloverService rolloverService;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    @Qualifier("backfillExecutor")
    private Executor backfillExecutor;

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

        UserMission first = builderService.pickAndBuild(ctx, guaranteed, expiresAt, MissionPool.daily, rng,
                usedCategoryIds, cache, null);
        if (first != null) {
            assigned.add(userMissionRepository.save(first));
            if (first.getCategory() != null)
                usedCategoryIds.add(first.getCategory().getId());
        }

        for (int i = assigned.size(); i < DAILY_MISSION_COUNT; i++) {
            UserMission next = builderService.pickAndBuild(ctx, normal, expiresAt, MissionPool.daily, rng,
                    usedCategoryIds, cache, null);
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
            UserMission mission = builderService.pickAndBuildForCategory(ctx, cache.weekly(), categories.get(i),
                    expiresAt, MissionPool.weekly, rng, cache, forced, usedTemplateIds);
            if (mission != null) {
                assigned.add(userMissionRepository.save(mission));
                usedTemplateIds.add(mission.getTemplate().getId());
            }
        }
        return assigned;
    }
}
