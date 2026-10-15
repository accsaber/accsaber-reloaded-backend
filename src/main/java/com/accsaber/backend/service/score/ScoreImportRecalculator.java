package com.accsaber.backend.service.score;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.service.campaign.CampaignEvaluationService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.score.ScoreRecalculationService.PlayerRecalc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
class ScoreImportRecalculator {

    private final ScoreRankingService scoreRankingService;
    private final MapDifficultyStatisticsService mapDifficultyStatisticsService;
    private final ScoreRecalculationService scoreRecalculationService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final UserCampaignRepository userCampaignRepository;
    @Qualifier("backfillExecutor")
    private final Executor backfillExecutor;

    void recalculateAfterImport(Map<MapDifficulty, Set<Long>> imported, boolean syncRankWhenSet) {
        Map<MapDifficulty, Set<Long>> changed = imported.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (changed.isEmpty()) {
            log.info("No new scores imported - skipping recalc");
            return;
        }

        Map<UUID, Set<Long>> usersByCategory = new HashMap<>();
        Set<UUID> overallCategoryIds = new HashSet<>();
        Set<Long> allAffectedUsers = new HashSet<>();
        changed.forEach((difficulty, users) -> {
            UUID categoryId = difficulty.getCategory().getId();
            usersByCategory.computeIfAbsent(categoryId, k -> new HashSet<>()).addAll(users);
            allAffectedUsers.addAll(users);
            if (difficulty.getCategory().isCountForOverall()) {
                overallCategoryIds.add(categoryId);
            }
        });

        log.info("Post-import recalc: {} difficulties, {} unique users, {} categories",
                changed.size(), allAffectedUsers.size(), usersByCategory.size());

        ImportConcurrency.fanOut(changed.keySet(), backfillExecutor, difficulty -> {
            try {
                if (syncRankWhenSet) {
                    scoreRankingService.reassignRanksForBackfill(difficulty.getId());
                } else {
                    scoreRankingService.reassignRanks(difficulty.getId());
                }
                mapDifficultyStatisticsService.recalculate(difficulty, null);
            } catch (Exception ex) {
                log.error("Per-difficulty recalc failed for {}: {}", difficulty.getId(), ex.getMessage());
            }
            return null;
        });

        scoreRecalculationService.recalculatePlayers(usersByCategory, overallCategoryIds, PlayerRecalc.SCORE_IMPORT);

        ImportConcurrency.fanOut(allAffectedUsers, backfillExecutor, userId -> {
            try {
                campaignEvaluationService.evaluateParticipatingForUser(userId);
            } catch (Exception ex) {
                log.error("Campaign evaluation failed for user {} after import: {}", userId, ex.getMessage());
            }
            return null;
        });

        log.info("Post-import recalc complete: {} users", allAffectedUsers.size());
    }

    void recoverCampaignsForParticipants(Set<Long> alreadySettled) {
        List<Long> pending = userCampaignRepository
                .findUserIdsByStatusesAndCampaignReleased(UserCampaignStatus.PARTICIPATING, CampaignStatus.DRAFT).stream()
                .filter(userId -> !alreadySettled.contains(userId))
                .toList();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Re-settling campaigns for {} participants not covered by gap-fill imports", pending.size());
        ImportConcurrency.fanOut(pending, backfillExecutor, userId -> {
            try {
                campaignEvaluationService.evaluateParticipatingForUser(userId);
            } catch (Exception e) {
                log.error("Campaign re-settle failed for user {} during gap-fill recovery: {}",
                        userId, e.getMessage());
            }
            return null;
        });
    }
}
