package com.accsaber.backend.service.mission;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.mission.MissionContributorResponse;
import com.accsaber.backend.model.dto.response.mission.MissionResponse;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.mission.MissionContribution;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.event.SharedMissionCompletedEvent;
import com.accsaber.backend.repository.mission.MissionContributionRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharedMissionService {

    private static final int REWARD_PAGE_SIZE = 200;
    private static final Sort JOIN_ORDER = Sort.by("firstAt");
    private static final Sort CONTRIBUTION_ORDER = Sort.by(Sort.Direction.DESC, "contribution").and(JOIN_ORDER);

    private final MissionTemplateRepository templateRepository;
    private final UserMissionRepository userMissionRepository;
    private final MissionContributionRepository contributionRepository;
    private final SharedMissionContextLoader sharedMissionContextLoader;
    private final MissionRowFactory missionRowFactory;
    private final LevelUpAwardService levelUpAwardService;
    private final ItemService itemService;
    private final MissionProgressService missionProgressService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "${accsaber.scheduler.community-mission-cron:0 20 * * * *}")
    public void runSweep() {
        openMissing();
        for (UUID missionId : contributionRepository.findMissionIdsAwaitingRewards()) {
            payRewards(missionId);
        }
    }

    @Async("backfillExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(SharedMissionCompletedEvent event) {
        payRewards(event.missionId());
        openMissing();
    }

    @Transactional(readOnly = true)
    public List<MissionResponse> list(UUID eventId, boolean activeOnly, Long viewerId) {
        List<UserMission> missions = userMissionRepository.findCommunity(eventId, activeOnly);
        MissionResponse.SharedContext ctx = sharedMissionContextLoader.load(missions, viewerId);
        return missions.stream().map(m -> MissionResponse.from(m, ctx)).toList();
    }

    @Transactional(readOnly = true)
    public MissionResponse get(UUID missionId, Long viewerId) {
        UserMission mission = userMissionRepository.findCommunityById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("CommunityMission", missionId));
        return MissionResponse.from(mission, sharedMissionContextLoader.load(List.of(mission), viewerId));
    }

    @Transactional(readOnly = true)
    public Page<MissionContributorResponse> leaderboard(UUID missionId, Pageable pageable) {
        if (userMissionRepository.findCommunityById(missionId).isEmpty()) {
            throw new ResourceNotFoundException("CommunityMission", missionId);
        }
        return sharedMissionContextLoader.contributors(missionId, pageable);
    }

    public int openMissing() {
        List<MissionTemplate> templates = templateRepository.findActiveCommunityTemplates();
        if (templates.isEmpty()) {
            return 0;
        }
        Set<UUID> alreadyOpen = Set.copyOf(userMissionRepository.findTemplateIdsWithActiveCommunityMission());
        Instant now = Instant.now();
        int opened = 0;
        for (MissionTemplate template : templates) {
            if (alreadyOpen.contains(template.getId()) || !isOpenNow(template, now)) {
                continue;
            }
            if (completionsSoFar(template) >= allowedCompletions(template)) {
                continue;
            }
            if (open(template)) {
                opened++;
            }
        }
        if (opened > 0) {
            log.info("Opened {} community missions", opened);
        }
        return opened;
    }

    private boolean isOpenNow(MissionTemplate template, Instant now) {
        Event event = template.getEvent();
        if (event != null && !event.isLive(now)) {
            return false;
        }
        return template.isOpenAt(event, now);
    }

    private boolean open(MissionTemplate template) {
        try {
            transactionTemplate.executeWithoutResult(status -> userMissionRepository
                    .save(missionRowFactory.build(null, template, template.getEvent())));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private long completionsSoFar(MissionTemplate template) {
        return userMissionRepository.countByTemplate_IdAndUserIsNullAndStatus(
                template.getId(), MissionStatus.completed);
    }

    private long allowedCompletions(MissionTemplate template) {
        if (!template.isRepeatable()) {
            return 1;
        }
        return template.getMaxCompletions() != null ? template.getMaxCompletions() : Long.MAX_VALUE;
    }

    public void payRewards(UUID missionId) {
        UserMission mission = userMissionRepository.findSharedById(missionId).orElse(null);
        if (mission == null || mission.getStatus() != MissionStatus.completed) {
            return;
        }
        Sort order = mission.isCommunity() ? JOIN_ORDER : CONTRIBUTION_ORDER;
        String rewardLabel = (mission.isCommunity() ? "Community" : "Clan") + " mission reward: ";
        int xpReward = mission.getXpReward() != null ? mission.getXpReward() : 0;
        UUID itemRewardId = mission.getItemReward() != null ? mission.getItemReward().getId() : null;
        String missionName = mission.getTemplate().getName();
        if (xpReward <= 0 && itemRewardId == null) {
            markAllRewarded(missionId);
            return;
        }
        int paid = 0;
        while (true) {
            List<MissionContribution> page = contributionRepository
                    .findUnrewarded(missionId, PageRequest.of(0, REWARD_PAGE_SIZE, order));
            if (page.isEmpty()) {
                break;
            }
            int paidInPage = 0;
            for (MissionContribution contribution : page) {
                if (payOne(missionId, contribution.getUser().getId(), xpReward, itemRewardId,
                        rewardLabel + missionName)) {
                    paidInPage++;
                }
            }
            if (paidInPage == 0) {
                log.error("Shared mission {} has {} contributors that could not be rewarded",
                        missionId, page.size());
                break;
            }
            paid += paidInPage;
        }
        if (paid > 0) {
            log.info("Paid shared mission '{}' rewards to {} contributors", missionName, paid);
        }
    }

    private boolean payOne(UUID missionId, Long userId, int xpReward, UUID itemRewardId, String reason) {
        try {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                if (contributionRepository.markRewarded(missionId, userId, Instant.now()) == 0) {
                    return false;
                }
                if (xpReward > 0) {
                    levelUpAwardService.addMissionXp(userId, (double) (xpReward));
                    missionProgressService.creditXp(userId, (double) (xpReward));
                }
                if (itemRewardId != null) {
                    itemService.awardSystem(userId, itemRewardId, ItemSource.mission, missionId.toString(),
                            reason);
                }
                return true;
            }));
        } catch (Exception e) {
            log.warn("Failed to reward user {} for shared mission {}: {}", userId, missionId, e.getMessage());
            return false;
        }
    }

    private void markAllRewarded(UUID missionId) {
        while (true) {
            List<MissionContribution> page = contributionRepository
                    .findUnrewarded(missionId, PageRequest.of(0, REWARD_PAGE_SIZE, JOIN_ORDER));
            if (page.isEmpty()) {
                return;
            }
            transactionTemplate.executeWithoutResult(status -> {
                for (MissionContribution contribution : page) {
                    contributionRepository.markRewarded(missionId, contribution.getUser().getId(), Instant.now());
                }
            });
        }
    }
}
