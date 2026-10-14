package com.accsaber.backend.scheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.milestone.LevelThresholdRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.LevelUpAwardService;
import com.accsaber.backend.service.milestone.LevelService;
import com.accsaber.backend.util.SqlValues;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LevelRewardScheduler {

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final LevelThresholdRepository levelThresholdRepository;
    private final LevelService levelService;
    private final LevelUpAwardService levelUpAwardService;

    @Value("${accsaber.level-rewards.max-grants-per-run:400}")
    private int maxGrantsPerRun;

    @Value("${accsaber.level-rewards.max-users-per-run:2000}")
    private int maxUsersPerRun;

    private record PendingGrant(Long userId, int level, Instant crossedAt) {
    }

    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void grantMissingLevelRewards() {
        Map<Integer, Double> thresholds = rewardLevelThresholds();
        if (thresholds.isEmpty()) {
            return;
        }

        Map<Long, List<Integer>> missingByUser = findMissingRewards(thresholds);
        if (missingByUser.isEmpty()) {
            return;
        }
        if (missingByUser.size() > maxUsersPerRun) {
            log.warn("Level reward sweep skipped: {} users are missing rewards, above the {} cap."
                    + " Raise accsaber.level-rewards.max-users-per-run deliberately, since truncating"
                    + " would hand out serial numbers out of achievement order",
                    missingByUser.size(), maxUsersPerRun);
            return;
        }

        List<PendingGrant> pending = resolveCrossings(missingByUser, thresholds);
        pending.sort(Comparator
                .comparing(PendingGrant::crossedAt)
                .thenComparing(PendingGrant::level)
                .thenComparing(PendingGrant::userId));

        int granted = 0;
        for (PendingGrant grant : pending) {
            if (granted >= maxGrantsPerRun) {
                break;
            }
            try {
                levelUpAwardService.grantLevelRewards(grant.userId(), grant.level());
                granted++;
            } catch (Exception e) {
                log.error("Failed to grant level {} rewards to user {}: {}",
                        grant.level(), grant.userId(), e.getMessage(), e);
            }
        }

        log.info("Level reward sweep granted {} of {} pending (user, level) rewards across {} users",
                granted, pending.size(), missingByUser.size());
    }

    private Map<Integer, Double> rewardLevelThresholds() {
        TreeSet<Integer> levels = new TreeSet<>(itemRepository.findDistinctUnlockLevels());
        levels.addAll(levelThresholdRepository.findLevelsWithItemAwards());
        if (levels.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Double> thresholds = new LinkedHashMap<>();
        double cumulative = 0.0;
        int level = 0;
        for (int rewardLevel : levels) {
            while (level < rewardLevel) {
                level++;
                cumulative += levelService.xpForLevel(level);
            }
            thresholds.put(rewardLevel, cumulative);
        }
        return thresholds;
    }

    private Map<Long, List<Integer>> findMissingRewards(Map<Integer, Double> thresholds) {
        Map<Long, List<Integer>> missingByUser = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> threshold : thresholds.entrySet()) {
            List<Long> userIds = userRepository
                    .findUsersMissingLevelReward(threshold.getKey(), threshold.getValue());
            for (Long userId : userIds) {
                missingByUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(threshold.getKey());
            }
        }
        return missingByUser;
    }

    private List<PendingGrant> resolveCrossings(Map<Long, List<Integer>> missingByUser,
            Map<Integer, Double> thresholds) {
        List<PendingGrant> pending = new ArrayList<>();
        for (Map.Entry<Long, List<Integer>> entry : missingByUser.entrySet()) {
            Long userId = entry.getKey();
            List<Integer> levels = entry.getValue();
            Map<Integer, Instant> crossings = crossingTimes(userId, levels, thresholds);
            for (int level : levels) {
                pending.add(new PendingGrant(userId, level,
                        crossings.getOrDefault(level, Instant.EPOCH)));
            }
        }
        return pending;
    }

    private Map<Integer, Instant> crossingTimes(Long userId, List<Integer> levels,
            Map<Integer, Double> thresholds) {
        Map<Integer, Instant> crossings = new LinkedHashMap<>();
        double cumulative = 0.0;
        Instant last = Instant.EPOCH;

        for (Object[] event : userRepository.findXpTimeline(userId)) {
            Instant at = SqlValues.toInstant(event[0]);
            if (at != null) {
                last = at;
            }
            cumulative += ((Number) event[1]).doubleValue();
            for (int level : levels) {
                if (!crossings.containsKey(level) && cumulative >= thresholds.get(level)) {
                    crossings.put(level, last);
                }
            }
            if (crossings.size() == levels.size()) {
                break;
            }
        }
        return crossings;
    }
}
