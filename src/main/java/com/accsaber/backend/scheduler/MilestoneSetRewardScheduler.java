package com.accsaber.backend.scheduler;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.repository.milestone.UserMilestoneSetBonusRepository;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.util.SqlValues;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MilestoneSetRewardScheduler {

    private final UserMilestoneSetBonusRepository userMilestoneSetBonusRepository;
    private final MilestoneEvaluationService milestoneEvaluationService;

    @Value("${accsaber.milestone-set-rewards.max-sets-per-run:400}")
    private int maxSetsPerRun;

    private record PendingSet(Long userId, UUID setId, Instant earnedAt) {
    }

    @Scheduled(fixedRate = 900_000, initialDelay = 120_000)
    public void grantMissingSetRewards() {
        List<PendingSet> pending = pendingSets();
        if (pending.isEmpty()) {
            return;
        }

        int granted = 0;
        for (PendingSet entry : pending) {
            try {
                granted += milestoneEvaluationService.settleSetRewards(
                        entry.userId(), entry.setId(), entry.earnedAt());
            } catch (Exception e) {
                log.error("Failed to settle milestone set {} for user {}: {}",
                        entry.setId(), entry.userId(), e.getMessage(), e);
            }
        }

        log.info("Milestone set reward sweep settled {} (user, set) pairs and granted {} items",
                pending.size(), granted);
    }

    private List<PendingSet> pendingSets() {
        return Stream.concat(
                userMilestoneSetBonusRepository.findUnclaimedSetCompletions(maxSetsPerRun).stream(),
                userMilestoneSetBonusRepository.findBonusesMissingRewards(maxSetsPerRun).stream())
                .map(row -> new PendingSet(
                        SqlValues.toLong(row[0]),
                        SqlValues.toUuid(row[1]),
                        Objects.requireNonNullElse(SqlValues.toInstant(row[2]), Instant.EPOCH)))
                .sorted(Comparator.comparing(PendingSet::earnedAt)
                        .thenComparing(PendingSet::userId)
                        .thenComparing(PendingSet::setId))
                .limit(maxSetsPerRun)
                .toList();
    }
}
