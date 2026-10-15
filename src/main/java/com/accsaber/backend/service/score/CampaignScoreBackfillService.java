package com.accsaber.backend.service.score;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.event.LegacyCampaignBackfillEvent;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.campaign.CampaignEvaluationService;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignScoreBackfillService {

    private static final int MAX_CONCURRENT_USERS = 2;

    private final ScoreImporter importer;
    private final CampaignRepository campaignRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final UserCampaignRepository userCampaignRepository;
    private final CampaignEvaluationService campaignEvaluationService;
    private final DuplicateUserService duplicateUserService;
    private final ModifierCacheService modifierCacheService;
    private final ScoreRepository scoreRepository;
    @Qualifier("backfillExecutor")
    private final Executor backfillExecutor;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLegacyCampaignStarted(LegacyCampaignBackfillEvent event) {
        backfillAndSettleLegacyCampaign(event.userId(), event.campaignId());
    }

    public CompletableFuture<Void> recheckLegacyCampaign(UUID campaignId, Long userId) {
        Campaign campaign = campaignRepository.findByIdAndActiveTrue(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        if (!campaign.isLegacy()) {
            throw new ValidationException("Score re-check is only available for legacy campaigns");
        }
        List<Long> targets = userId != null
                ? List.of(duplicateUserService.resolvePrimaryUserId(userId))
                : userCampaignRepository.findUserIdsByCampaignAndStatuses(campaignId, UserCampaignStatus.PARTICIPATING);
        if (targets.isEmpty()) {
            log.info("Legacy campaign re-check for campaign {} has no participants", campaignId);
            return CompletableFuture.completedFuture(null);
        }
        log.info("Legacy campaign re-check queued for campaign {} across {} user(s)", campaignId, targets.size());
        return CompletableFuture.runAsync(() -> runLegacyRecheck(campaignId, targets), backfillExecutor);
    }

    public CompletableFuture<Void> resettleCampaign(UUID campaignId, Long userId) {
        campaignRepository.findByIdAndActiveTrue(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        List<Long> targets = userId != null
                ? List.of(duplicateUserService.resolvePrimaryUserId(userId))
                : userCampaignRepository.findUserIdsByCampaignAndStatuses(campaignId, UserCampaignStatus.PARTICIPATING);
        if (targets.isEmpty()) {
            log.info("Campaign re-settle for campaign {} has no participants", campaignId);
            return CompletableFuture.completedFuture(null);
        }
        log.info("Campaign re-settle queued for campaign {} across {} user(s)", campaignId, targets.size());
        return CompletableFuture.runAsync(() -> runResettle(campaignId, targets), backfillExecutor);
    }

    public void backfillAndSettleLegacyCampaign(Long userId, UUID campaignId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Map<String, UUID> modifiers = modifierCacheService.getModifierCodeToId();
        int recorded = 0;
        for (var node : campaignDifficultyRepository.findActiveWithMapByCampaignId(campaignId)) {
            if (node.isBarrier()) {
                continue;
            }
            MapDifficulty md = node.getMapDifficulty();
            if (md == null || md.getStatus() == MapDifficultyStatus.RANKED) {
                continue;
            }
            if (!scoreRepository.findEligibleCampaignRows(resolved, List.of(md.getId()), Instant.EPOCH).isEmpty()) {
                continue;
            }
            try {
                boolean bl = importer.recordCampaignScore(importer.beatLeader(), resolved, md, modifiers);
                boolean ss = importer.recordCampaignScore(importer.scoreSaber(), resolved, md, modifiers);
                if (bl || ss) {
                    recorded++;
                }
            } catch (Exception e) {
                log.warn("Campaign backfill failed for user={} difficulty={}: {}", resolved, md.getId(),
                        e.getMessage());
            }
        }
        log.info("Legacy campaign backfill user={} campaign={}: recorded {} platform score(s)", resolved, campaignId,
                recorded);
        campaignEvaluationService.importLegacyScores(resolved, campaignId);
    }

    private void runResettle(UUID campaignId, List<Long> userIds) {
        long start = System.currentTimeMillis();
        for (Long userId : userIds) {
            try {
                campaignEvaluationService.resettleForUser(userId, campaignId);
            } catch (Exception e) {
                log.error("Campaign re-settle failed for user {} campaign {}: {}", userId, campaignId, e.getMessage());
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("Campaign re-settle complete for campaign {} across {} user(s) in {}s",
                campaignId, userIds.size(), elapsed / 1000);
    }

    private void runLegacyRecheck(UUID campaignId, List<Long> userIds) {
        long start = System.currentTimeMillis();
        ImportConcurrency.forEachBounded(userIds, MAX_CONCURRENT_USERS, userId -> {
            try {
                backfillAndSettleLegacyCampaign(userId, campaignId);
            } catch (Exception e) {
                log.error("Legacy campaign re-check failed for user {} campaign {}: {}",
                        userId, campaignId, e.getMessage());
            }
        });
        long elapsed = System.currentTimeMillis() - start;
        log.info("Legacy campaign re-check complete for campaign {} across {} user(s) in {}s",
                campaignId, userIds.size(), elapsed / 1000);
    }
}
