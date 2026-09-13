package com.accsaber.backend.service.milestone;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.event.CampaignCompletedEvent;
import com.accsaber.backend.model.event.CampaignNodeCompletedEvent;
import com.accsaber.backend.model.event.CrateOpenedEvent;
import com.accsaber.backend.model.event.InventoryChangedEvent;
import com.accsaber.backend.model.event.MarketListingEvent;
import com.accsaber.backend.model.event.MissionCompletedEvent;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.service.milestone.source.MilestoneTrigger;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MilestoneTriggerListener {

    private final MilestoneEvaluationService evaluationService;
    private final CampaignRepository campaignRepository;
    private final Executor taskExecutor;
    private final Duration inventoryQuiet;
    private final Duration inventoryMaxWait;
    private final Map<Long, SettleWindow> settlingInventories = new ConcurrentHashMap<>();

    public MilestoneTriggerListener(MilestoneEvaluationService evaluationService,
            CampaignRepository campaignRepository,
            @Qualifier("taskExecutor") Executor taskExecutor,
            @Value("${accsaber.milestones.inventory-quiet-seconds:10}") long inventoryQuietSeconds,
            @Value("${accsaber.milestones.inventory-max-wait-seconds:60}") long inventoryMaxWaitSeconds) {
        this.evaluationService = evaluationService;
        this.campaignRepository = campaignRepository;
        this.taskExecutor = taskExecutor;
        this.inventoryQuiet = Duration.ofSeconds(inventoryQuietSeconds);
        this.inventoryMaxWait = Duration.ofSeconds(inventoryMaxWaitSeconds);
    }

    private record SettleWindow(Instant firstChange, Instant lastChange) {

        SettleWindow touch(Instant now) {
            return new SettleWindow(firstChange, now);
        }

        boolean settled(Instant now, Duration quiet, Duration maxWait) {
            return !lastChange.plus(quiet).isAfter(now) || !firstChange.plus(maxWait).isAfter(now);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCrateOpened(CrateOpenedEvent event) {
        if (event.payload() == null || event.payload().player() == null) {
            return;
        }
        dispatch(userId(event.payload().player()), MilestoneTrigger.ITEM);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInventoryChanged(InventoryChangedEvent event) {
        if (event.userId() == null) {
            return;
        }
        Instant now = Instant.now();
        settlingInventories.merge(event.userId(), new SettleWindow(now, now), (open, ignored) -> open.touch(now));
    }

    @Scheduled(fixedDelayString = "${accsaber.milestones.inventory-flush-millis:5000}")
    public void flushSettledInventories() {
        Instant now = Instant.now();
        for (Map.Entry<Long, SettleWindow> entry : settlingInventories.entrySet()) {
            SettleWindow window = entry.getValue();
            if (!window.settled(now, inventoryQuiet, inventoryMaxWait)) {
                continue;
            }
            if (settlingInventories.remove(entry.getKey(), window)) {
                Long userId = entry.getKey();
                taskExecutor.execute(() -> dispatch(userId, MilestoneTrigger.ITEM));
            }
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMarketListing(MarketListingEvent event) {
        Long actorId = userId(event.actor());
        Long sellerId = userId(event.seller());
        dispatch(actorId, MilestoneTrigger.MARKET);
        if (!Objects.equals(actorId, sellerId)) {
            dispatch(sellerId, MilestoneTrigger.MARKET);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMissionCompleted(MissionCompletedEvent event) {
        if (event.payload() == null) {
            return;
        }
        dispatch(event.payload().getUserId(), MilestoneTrigger.MISSION);
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCampaignCompleted(CampaignCompletedEvent event) {
        if (event.silent()) {
            return;
        }
        dispatch(event.userId(), MilestoneTrigger.CAMPAIGN);
        campaignRepository.findCreatorIdByIdAndActiveTrue(event.campaignId())
                .filter(creatorId -> !creatorId.equals(event.userId()))
                .ifPresent(creatorId -> dispatch(creatorId, MilestoneTrigger.CAMPAIGN));
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCampaignNodeCompleted(CampaignNodeCompletedEvent event) {
        if (event.silent()) {
            return;
        }
        dispatch(event.userId(), MilestoneTrigger.CAMPAIGN);
    }

    private Long userId(PlayerRef ref) {
        return ref == null ? null : Long.valueOf(ref.id());
    }

    private void dispatch(Long userId, MilestoneTrigger trigger) {
        if (userId == null) {
            return;
        }
        try {
            evaluationService.evaluateForTrigger(userId, trigger);
        } catch (Exception e) {
            log.error("Milestone {} evaluation failed for user {}: {}", trigger, userId, e.getMessage(), e);
        }
    }
}
