package com.accsaber.backend.service.milestone;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.milestone.MilestoneCompletedResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneRewardResponse;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneItem;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneSetItem;
import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;
import com.accsaber.backend.model.entity.milestone.UserMilestoneSetBonus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.MilestoneCompletedEvent;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.repository.milestone.MilestoneItemRepository;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetItemRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneSetBonusRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemMapper;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;
import com.accsaber.backend.service.milestone.MilestoneQueryBuilderService.Progress;
import com.accsaber.backend.service.milestone.source.MilestoneSourceRegistry;
import com.accsaber.backend.service.milestone.source.MilestoneTrigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneEvaluationService {

    private static final Progress EMPTY_PROGRESS = new Progress(null, null);

    private final MilestoneRepository milestoneRepository;
    private final MilestoneItemRepository milestoneItemRepository;
    private final MilestoneSetItemRepository milestoneSetItemRepository;
    private final MilestoneSetRepository milestoneSetRepository;
    private final UserMilestoneLinkRepository userMilestoneLinkRepository;
    private final UserMilestoneSetBonusRepository userMilestoneSetBonusRepository;
    private final UserRepository userRepository;
    private final MilestoneQueryBuilderService queryBuilderService;
    private final ItemService itemService;
    private final UserItemLinkRepository userItemLinkRepository;
    private final LevelUpAwardService levelUpAwardService;
    private final MilestoneSourceRegistry sourceRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public record EvaluationResult(List<Milestone> completedMilestones, List<MilestoneSet> completedSets) {
    }

    @Transactional
    public EvaluationResult evaluateAfterScore(Long userId, Score newScore) {
        UUID categoryId = newScore.getMapDifficulty().getCategory().getId();
        UUID mapDifficultyId = newScore.getMapDifficulty().getId();
        return completeAll(userId, milestoneRepository.findActiveUncompletedForUserScoped(
                userId, categoryId, mapDifficultyId), newScore);
    }

    @Transactional
    public void evaluateSingleMilestoneForUser(Long userId, Milestone milestone) {
        UserMilestoneLink link = getOrCreateLink(userId, milestone);
        if (link.isCompleted()) {
            return;
        }

        UUID categoryId = milestone.getCategory() != null ? milestone.getCategory().getId() : null;
        Progress progress = queryBuilderService.evaluateProgress(milestone.getQuerySpec(), userId, categoryId);
        link.setProgress(progress.value());
        link.setGateFraction(progress.gateFraction());

        boolean newlyCompleted = isCompleted(milestone, progress.value());
        if (newlyCompleted) {
            markCompleted(link, findQualifying(milestone, userId));
        }

        userMilestoneLinkRepository.save(link);

        if (newlyCompleted) {
            finishCompletion(userId, List.of(milestone));
        }
    }

    @Transactional
    public EvaluationResult evaluateForTrigger(Long userId, MilestoneTrigger trigger) {
        List<String> sources = sourceRegistry.namesFor(trigger);
        if (sources.isEmpty()) {
            return new EvaluationResult(List.of(), List.of());
        }
        return completeAll(userId,
                milestoneRepository.findActiveUncompletedForUserBySources(userId, sources), null);
    }

    @Transactional
    public EvaluationResult evaluateAllForUser(Long userId) {
        return completeAll(userId, milestoneRepository.findActiveUncompletedForUser(userId), null);
    }

    private EvaluationResult completeAll(Long userId, List<Milestone> uncompleted, Score newScore) {
        if (uncompleted.isEmpty()) {
            return new EvaluationResult(List.of(), List.of());
        }

        Map<UUID, Progress> batchResults = evaluateAll(uncompleted, userId);
        Map<UUID, UserMilestoneLink> linkMap = loadLinkMap(userId, uncompleted);

        List<Milestone> newlyCompleted = new ArrayList<>();
        List<UserMilestoneLink> linksToSave = new ArrayList<>();

        for (Milestone milestone : uncompleted) {
            Progress progress = batchResults.getOrDefault(milestone.getId(), EMPTY_PROGRESS);
            Double currentValue = progress.value();
            UserMilestoneLink link = getOrCreateFromMap(linkMap, userId, milestone);
            link.setProgress(currentValue);
            link.setGateFraction(progress.gateFraction());

            if (isCompleted(milestone, currentValue) && !link.isCompleted()) {
                markCompleted(link, newScore != null ? newScore : findQualifying(milestone, userId));
                newlyCompleted.add(milestone);
            }

            linksToSave.add(link);
        }

        userMilestoneLinkRepository.saveAll(linksToSave);
        return finishCompletion(userId, newlyCompleted);
    }

    private void markCompleted(UserMilestoneLink link, Score qualifying) {
        link.setCompleted(true);
        link.setAchievedWithScore(qualifying);
        link.setCompletedAt(resolveCompletionTime(qualifying));
    }

    private EvaluationResult finishCompletion(Long userId, List<Milestone> milestones) {
        Map<UUID, List<MilestoneRewardResponse>> rewards = awardMilestoneItems(userId, milestones);
        List<MilestoneSet> completedSets = claimEligibleSetBonuses(userId, milestones);
        Map<UUID, List<MilestoneRewardResponse>> setRewards = awardSetItems(userId, completedSets);
        awardCompletionXp(userId, milestones, completedSets);
        publishCompletionEvent(userId, milestones, completedSets, rewards, setRewards);
        return new EvaluationResult(milestones, completedSets);
    }

    private Score findQualifying(Milestone milestone, Long userId) {
        if (milestone.getQuerySpec() == null
                || queryBuilderService.requiresIndividualEvaluation(milestone.getQuerySpec())) {
            return null;
        }
        UUID categoryId = milestone.getCategory() != null ? milestone.getCategory().getId() : null;
        return queryBuilderService.findQualifyingScore(milestone.getQuerySpec(), userId, categoryId,
                milestone.getTargetValue(), milestone.getComparison());
    }

    private void publishCompletionEvent(Long userId, List<Milestone> milestones, List<MilestoneSet> sets,
            Map<UUID, List<MilestoneRewardResponse>> rewards, Map<UUID, List<MilestoneRewardResponse>> setRewards) {
        if (milestones.isEmpty() && sets.isEmpty())
            return;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return;

        List<MilestoneCompletedResponse.CompletedMilestone> milestonePayloads = milestones.stream()
                .map(m -> toMilestonePayload(m, rewards))
                .toList();
        List<MilestoneCompletedResponse.CompletedSet> setPayloads = sets.stream()
                .map(s -> toSetPayload(s, setRewards))
                .toList();

        MilestoneCompletedResponse payload = MilestoneCompletedResponse.builder()
                .userId(userId)
                .userName(user.getName())
                .userCountry(user.getCountry())
                .userAvatarUrl(user.getAvatarUrl())
                .userCdnAvatarUrl(user.getCdnAvatarUrl())
                .completedAt(Instant.now())
                .milestones(milestonePayloads.isEmpty() ? null : milestonePayloads)
                .sets(setPayloads.isEmpty() ? null : setPayloads)
                .build();

        eventPublisher.publishEvent(new MilestoneCompletedEvent(payload));
    }

    private MilestoneCompletedResponse.CompletedMilestone toMilestonePayload(Milestone m,
            Map<UUID, List<MilestoneRewardResponse>> rewards) {
        return MilestoneCompletedResponse.CompletedMilestone.builder()
                .id(m.getId())
                .setId(m.getMilestoneSet() != null ? m.getMilestoneSet().getId() : null)
                .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                .title(m.getTitle())
                .description(m.getDescription())
                .type(m.getType())
                .tier(m.getTier() != null ? m.getTier().name() : null)
                .xp(m.getXp())
                .rewards(rewards.get(m.getId()))
                .build();
    }

    private MilestoneCompletedResponse.CompletedSet toSetPayload(MilestoneSet s,
            Map<UUID, List<MilestoneRewardResponse>> setRewards) {
        return MilestoneCompletedResponse.CompletedSet.builder()
                .id(s.getId())
                .title(s.getTitle())
                .description(s.getDescription())
                .bonusXp(s.getSetBonusXp())
                .rewards(setRewards.get(s.getId()))
                .build();
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> regrantRewards(UUID milestoneId) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrueEager(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        int granted = regrantOne(milestone);
        log.info("Reward regrant complete for milestone '{}' ({}) - {} grants issued",
                milestone.getTitle(), milestoneId, granted);
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> regrantAllRewards() {
        List<UUID> milestoneIds = milestoneItemRepository.findDistinctMilestoneIdsWithRewards();
        log.info("Reward regrant started across {} milestones with rewards", milestoneIds.size());
        int granted = 0;
        for (UUID milestoneId : milestoneIds) {
            Milestone milestone = milestoneRepository.findByIdAndActiveTrueEager(milestoneId).orElse(null);
            if (milestone != null) {
                granted += regrantOne(milestone);
            }
        }
        log.info("Reward regrant complete across {} milestones - {} grants issued", milestoneIds.size(), granted);
        return CompletableFuture.completedFuture(null);
    }

    private int regrantOne(Milestone milestone) {
        List<MilestoneItem> rewards = milestoneItemRepository.findByMilestoneIds(List.of(milestone.getId()));
        if (rewards.isEmpty()) {
            return 0;
        }
        List<Long> completed = userMilestoneLinkRepository.findCompletedUserIdsByMilestone(milestone.getId());
        if (completed.isEmpty()) {
            return 0;
        }
        String sourceId = milestone.getId().toString();
        String reason = "Completed milestone: " + milestone.getTitle();
        int granted = 0;
        for (MilestoneItem reward : rewards) {
            if (!reward.getItem().isObtainableAt(Instant.now())) {
                continue;
            }
            Set<Long> alreadyHave = new HashSet<>(userItemLinkRepository.findUserIdsByItemAndSource(
                    reward.getItem().getId(), ItemSource.milestone, sourceId));
            for (Long userId : completed) {
                if (alreadyHave.contains(userId)) {
                    continue;
                }
                itemService.awardSystem(userId, reward.getItem().getId(), ItemSource.milestone, sourceId, reason,
                        reward.getQuantity());
                granted++;
            }
        }
        return granted;
    }

    private Map<UUID, List<MilestoneRewardResponse>> awardMilestoneItems(Long userId, List<Milestone> milestones) {
        if (milestones.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<MilestoneItem>> links = milestoneItemRepository
                .findByMilestoneIds(milestones.stream().map(Milestone::getId).toList()).stream()
                .collect(Collectors.groupingBy(link -> link.getMilestone().getId()));
        Map<UUID, List<MilestoneRewardResponse>> rewards = new HashMap<>();
        for (Milestone milestone : milestones) {
            for (MilestoneItem link : links.getOrDefault(milestone.getId(), List.of())) {
                rewards.computeIfAbsent(milestone.getId(), id -> new ArrayList<>())
                        .add(awardObtainableItem(userId, link.getItem(), link.getQuantity(), ItemSource.milestone,
                                milestone.getId().toString(), "Completed milestone: " + milestone.getTitle()));
            }
        }
        return rewards;
    }

    private Map<UUID, List<MilestoneRewardResponse>> awardSetItems(Long userId, List<MilestoneSet> sets) {
        if (sets.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<MilestoneSetItem>> links = milestoneSetItemRepository
                .findBySetIds(sets.stream().map(MilestoneSet::getId).toList()).stream()
                .collect(Collectors.groupingBy(link -> link.getMilestoneSet().getId()));
        Map<UUID, List<MilestoneRewardResponse>> rewards = new HashMap<>();
        for (MilestoneSet set : sets) {
            for (MilestoneSetItem link : links.getOrDefault(set.getId(), List.of())) {
                rewards.computeIfAbsent(set.getId(), id -> new ArrayList<>())
                        .add(awardObtainableItem(userId, link.getItem(), link.getQuantity(), ItemSource.milestone_set,
                                set.getId().toString(), "Completed milestone set: " + set.getTitle()));
            }
        }
        return rewards;
    }

    private MilestoneRewardResponse awardObtainableItem(Long userId, Item item, int quantity, ItemSource source,
            String sourceId, String reason) {
        if (item.isObtainableAt(Instant.now())) {
            itemService.awardSystem(userId, item.getId(), source, sourceId, reason, quantity);
        }
        return ItemMapper.toRewardResponse(item, quantity);
    }

    private Map<UUID, Progress> evaluateAll(List<Milestone> milestones, Long userId) {
        List<Milestone> batchable = new ArrayList<>();
        Map<UUID, Progress> results = new HashMap<>();

        for (Milestone m : milestones) {
            if (m.getQuerySpec() == null) {
                continue;
            } else if (queryBuilderService.requiresIndividualEvaluation(m.getQuerySpec())) {
                UUID catId = m.getCategory() != null ? m.getCategory().getId() : null;
                results.put(m.getId(), queryBuilderService.evaluateProgress(m.getQuerySpec(), userId, catId));
            } else {
                batchable.add(m);
            }
        }

        if (!batchable.isEmpty()) {
            queryBuilderService.evaluateBatch(batchable, userId)
                    .forEach((milestoneId, value) -> results.put(milestoneId, new Progress(value, null)));
        }
        return results;
    }

    private Map<UUID, UserMilestoneLink> loadLinkMap(Long userId, List<Milestone> milestones) {
        List<UUID> milestoneIds = milestones.stream().map(Milestone::getId).toList();
        return userMilestoneLinkRepository.findByUser_IdAndMilestone_IdIn(userId, milestoneIds).stream()
                .collect(Collectors.toMap(l -> l.getMilestone().getId(), Function.identity()));
    }

    private UserMilestoneLink getOrCreateFromMap(Map<UUID, UserMilestoneLink> linkMap, Long userId,
            Milestone milestone) {
        UserMilestoneLink existing = linkMap.get(milestone.getId());
        if (existing != null)
            return existing;
        return UserMilestoneLink.builder()
                .user(userRepository.getReferenceById(userId))
                .milestone(milestone)
                .build();
    }

    private List<MilestoneSet> claimEligibleSetBonuses(Long userId, List<Milestone> newlyCompleted) {
        List<MilestoneSet> earned = new ArrayList<>();
        Set<UUID> checked = new HashSet<>();

        for (Milestone milestone : newlyCompleted) {
            MilestoneSet set = milestone.getMilestoneSet();
            if (set == null || !checked.add(set.getId()))
                continue;
            if (claimSetBonus(userId, set, Instant.now()))
                earned.add(set);
        }

        return earned;
    }

    private boolean claimSetBonus(Long userId, MilestoneSet set, Instant claimedAt) {
        if (userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(userId, set.getId()))
            return false;

        long total = milestoneRepository.countActiveBySetId(set.getId());
        long completed = userMilestoneLinkRepository.countCompletedByUserAndSet(userId, set.getId());
        if (total == 0 || completed < total)
            return false;

        userMilestoneSetBonusRepository.save(UserMilestoneSetBonus.builder()
                .user(userRepository.getReferenceById(userId))
                .milestoneSet(set)
                .claimedAt(claimedAt)
                .build());
        return true;
    }

    @Transactional
    public int settleSetRewards(Long userId, UUID setId, Instant earnedAt) {
        MilestoneSet set = milestoneSetRepository.findById(setId).orElse(null);
        if (set == null) {
            return 0;
        }
        if (claimSetBonus(userId, set, earnedAt) && set.getSetBonusXp() > 0) {
            levelUpAwardService.addXp(userId, set.getSetBonusXp());
        }
        return grantMissingSetItems(userId, set);
    }

    private int grantMissingSetItems(Long userId, MilestoneSet set) {
        String sourceId = set.getId().toString();
        String reason = "Completed milestone set: " + set.getTitle();
        Instant now = Instant.now();
        int granted = 0;

        for (MilestoneSetItem link : milestoneSetItemRepository.findBySetIds(List.of(set.getId()))) {
            Item item = link.getItem();
            if (!item.isObtainableAt(now) || countPriorSetGrants(userId, item, sourceId) > 0) {
                continue;
            }
            itemService.awardSystem(userId, item.getId(), ItemSource.milestone_set, sourceId, reason,
                    link.getQuantity());
            granted++;
        }
        return granted;
    }

    private long countPriorSetGrants(Long userId, Item item, String sourceId) {
        if (ItemService.isActiveCrateSentinel(item)) {
            return userItemLinkRepository.countByUser_IdAndItem_Type_KeyAndSourceAndSourceId(
                    userId, item.getType().getKey(), ItemSource.milestone_set, sourceId);
        }
        return userItemLinkRepository.countByUser_IdAndItem_IdAndSourceAndSourceId(
                userId, item.getId(), ItemSource.milestone_set, sourceId);
    }

    private void awardCompletionXp(Long userId, List<Milestone> milestones, List<MilestoneSet> sets) {
        double total = 0.0;
        for (Milestone milestone : milestones) {
            total += milestone.getXp();
        }
        for (MilestoneSet set : sets) {
            total += set.getSetBonusXp();
        }
        if (total > 0) {
            levelUpAwardService.addXp(userId, total);
        }
    }

    private boolean isCompleted(Milestone milestone, Double currentValue) {
        if (currentValue == null || milestone.getTargetValue() == null)
            return false;
        return "LTE".equals(milestone.getComparison())
                ? currentValue.compareTo(milestone.getTargetValue()) <= 0
                : currentValue.compareTo(milestone.getTargetValue()) >= 0;
    }

    private Instant resolveCompletionTime(Score score) {
        return score != null && score.getTimeSet() != null ? score.getTimeSet() : Instant.now();
    }

    private UserMilestoneLink getOrCreateLink(Long userId, Milestone milestone) {
        return userMilestoneLinkRepository.findByUser_IdAndMilestone_Id(userId, milestone.getId())
                .orElseGet(() -> UserMilestoneLink.builder()
                        .user(userRepository.getReferenceById(userId))
                        .milestone(milestone)
                        .build());
    }
}
