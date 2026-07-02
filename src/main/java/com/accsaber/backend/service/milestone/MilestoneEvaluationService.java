package com.accsaber.backend.service.milestone;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.milestone.MilestoneCompletedResponse;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;
import com.accsaber.backend.model.entity.milestone.UserMilestoneSetBonus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.MilestoneCompletedEvent;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneSetBonusRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MilestoneEvaluationService {

    private final MilestoneRepository milestoneRepository;
    private final UserMilestoneLinkRepository userMilestoneLinkRepository;
    private final UserMilestoneSetBonusRepository userMilestoneSetBonusRepository;
    private final UserRepository userRepository;
    private final MilestoneQueryBuilderService queryBuilderService;
    private final ItemService itemService;
    private final LevelUpAwardService levelUpAwardService;
    private final ApplicationEventPublisher eventPublisher;

    public record EvaluationResult(List<Milestone> completedMilestones, List<MilestoneSet> completedSets) {
    }

    @Transactional
    public EvaluationResult evaluateAfterScore(Long userId, Score newScore) {
        UUID categoryId = newScore.getMapDifficulty().getCategory().getId();
        UUID mapDifficultyId = newScore.getMapDifficulty().getId();

        List<Milestone> uncompleted = milestoneRepository.findActiveUncompletedForUserScoped(
                userId, categoryId, mapDifficultyId);
        if (uncompleted.isEmpty())
            return new EvaluationResult(List.of(), List.of());

        boolean scoreIsFromBl = newScore.getBlScoreId() != null;
        List<Milestone> toEvaluate = uncompleted.stream()
                .filter(m -> !m.isBlExclusive() || scoreIsFromBl)
                .toList();
        if (toEvaluate.isEmpty())
            return new EvaluationResult(List.of(), List.of());

        Map<UUID, BigDecimal> batchResults = evaluateAll(toEvaluate, userId);
        Map<UUID, UserMilestoneLink> linkMap = loadLinkMap(userId, toEvaluate);

        List<Milestone> newlyCompleted = new ArrayList<>();
        List<UserMilestoneLink> linksToSave = new ArrayList<>();

        for (Milestone milestone : toEvaluate) {
            BigDecimal currentValue = batchResults.get(milestone.getId());
            UserMilestoneLink link = getOrCreateFromMap(linkMap, userId, milestone);
            link.setProgress(currentValue);

            if (isCompleted(milestone, currentValue) && !link.isCompleted()) {
                link.setCompleted(true);
                link.setCompletedAt(resolveCompletionTime(newScore));
                link.setAchievedWithScore(newScore);
                newlyCompleted.add(milestone);
            }

            linksToSave.add(link);
        }

        userMilestoneLinkRepository.saveAll(linksToSave);
        awardMilestoneItems(userId, newlyCompleted);

        List<MilestoneSet> completedSets = claimEligibleSetBonuses(userId, newlyCompleted);
        publishCompletionEvent(userId, newlyCompleted, completedSets);
        return new EvaluationResult(newlyCompleted, completedSets);
    }

    @Transactional
    public void evaluateSingleMilestoneForUser(Long userId, Milestone milestone) {
        UserMilestoneLink link = getOrCreateLink(userId, milestone);
        if (link.isCompleted()) {
            return;
        }

        UUID categoryId = milestone.getCategory() != null ? milestone.getCategory().getId() : null;
        BigDecimal currentValue = evaluateMilestone(milestone, userId, categoryId);
        link.setProgress(currentValue);

        boolean newlyCompleted = isCompleted(milestone, currentValue);
        if (newlyCompleted) {
            link.setCompleted(true);
            Score qualifying = null;
            if (milestone.getQuerySpec() != null
                    && !queryBuilderService.requiresIndividualEvaluation(milestone.getQuerySpec())) {
                qualifying = queryBuilderService.findQualifyingScore(
                        milestone.getQuerySpec(), userId, categoryId,
                        milestone.getTargetValue(), milestone.getComparison());
            }
            if (qualifying != null) {
                link.setAchievedWithScore(qualifying);
                link.setCompletedAt(resolveCompletionTime(qualifying));
            } else {
                link.setCompletedAt(Instant.now());
            }
        }

        userMilestoneLinkRepository.save(link);

        if (newlyCompleted) {
            awardMilestoneItems(userId, List.of(milestone));
            BigDecimal xpToAward = milestone.getXp() != null ? milestone.getXp() : BigDecimal.ZERO;
            List<MilestoneSet> completedSets = claimEligibleSetBonuses(userId, List.of(milestone));
            for (MilestoneSet set : completedSets) {
                xpToAward = xpToAward.add(set.getSetBonusXp() != null ? set.getSetBonusXp() : BigDecimal.ZERO);
            }
            if (xpToAward.compareTo(BigDecimal.ZERO) > 0) {
                awardXp(userId, xpToAward);
            }
            publishCompletionEvent(userId, List.of(milestone), completedSets);
        }
    }

    @Transactional
    public EvaluationResult evaluateAllForUser(Long userId) {
        List<Milestone> uncompleted = milestoneRepository.findActiveUncompletedForUser(userId);
        if (uncompleted.isEmpty())
            return new EvaluationResult(List.of(), List.of());

        Map<UUID, BigDecimal> batchResults = evaluateAll(uncompleted, userId);
        Map<UUID, UserMilestoneLink> linkMap = loadLinkMap(userId, uncompleted);

        List<Milestone> newlyCompleted = new ArrayList<>();
        List<UserMilestoneLink> linksToSave = new ArrayList<>();

        for (Milestone milestone : uncompleted) {
            BigDecimal currentValue = batchResults.get(milestone.getId());
            UserMilestoneLink link = getOrCreateFromMap(linkMap, userId, milestone);
            link.setProgress(currentValue);

            if (isCompleted(milestone, currentValue) && !link.isCompleted()) {
                link.setCompleted(true);
                UUID catId = milestone.getCategory() != null ? milestone.getCategory().getId() : null;
                Score qualifying = null;
                if (milestone.getQuerySpec() != null
                        && !queryBuilderService.requiresIndividualEvaluation(milestone.getQuerySpec())) {
                    qualifying = queryBuilderService.findQualifyingScore(
                            milestone.getQuerySpec(), userId, catId,
                            milestone.getTargetValue(), milestone.getComparison());
                }
                if (qualifying != null) {
                    link.setAchievedWithScore(qualifying);
                    link.setCompletedAt(resolveCompletionTime(qualifying));
                } else {
                    link.setCompletedAt(Instant.now());
                }
                newlyCompleted.add(milestone);
            }

            linksToSave.add(link);
        }

        userMilestoneLinkRepository.saveAll(linksToSave);
        awardMilestoneItems(userId, newlyCompleted);

        List<MilestoneSet> completedSets = claimEligibleSetBonuses(userId, newlyCompleted);
        publishCompletionEvent(userId, newlyCompleted, completedSets);
        return new EvaluationResult(newlyCompleted, completedSets);
    }

    private void publishCompletionEvent(Long userId, List<Milestone> milestones, List<MilestoneSet> sets) {
        if (milestones.isEmpty() && sets.isEmpty())
            return;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return;

        List<MilestoneCompletedResponse.CompletedMilestone> milestonePayloads = milestones.stream()
                .map(this::toMilestonePayload)
                .toList();
        List<MilestoneCompletedResponse.CompletedSet> setPayloads = sets.stream()
                .map(this::toSetPayload)
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

    private MilestoneCompletedResponse.CompletedMilestone toMilestonePayload(Milestone m) {
        return MilestoneCompletedResponse.CompletedMilestone.builder()
                .id(m.getId())
                .setId(m.getMilestoneSet() != null ? m.getMilestoneSet().getId() : null)
                .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                .title(m.getTitle())
                .description(m.getDescription())
                .type(m.getType())
                .tier(m.getTier() != null ? m.getTier().name() : null)
                .xp(m.getXp())
                .awardsItemId(m.getAwardsItem() != null ? m.getAwardsItem().getId() : null)
                .build();
    }

    private MilestoneCompletedResponse.CompletedSet toSetPayload(MilestoneSet s) {
        return MilestoneCompletedResponse.CompletedSet.builder()
                .id(s.getId())
                .title(s.getTitle())
                .description(s.getDescription())
                .bonusXp(s.getSetBonusXp())
                .awardsItemId(s.getAwardsItem() != null ? s.getAwardsItem().getId() : null)
                .build();
    }

    private void awardMilestoneItems(Long userId, List<Milestone> milestones) {
        for (Milestone m : milestones) {
            if (m.getAwardsItem() != null) {
                itemService.awardSystem(
                        userId,
                        m.getAwardsItem().getId(),
                        ItemSource.milestone,
                        m.getId().toString(),
                        "Completed milestone: " + m.getTitle());
            }
        }
    }

    private BigDecimal evaluateMilestone(Milestone milestone, Long userId, UUID categoryId) {
        if (milestone.getQuerySpec() == null)
            return null;
        return queryBuilderService.evaluate(milestone.getQuerySpec(), userId, categoryId);
    }

    private Map<UUID, BigDecimal> evaluateAll(List<Milestone> milestones, Long userId) {
        List<Milestone> batchable = new ArrayList<>();
        Map<UUID, BigDecimal> results = new HashMap<>();

        for (Milestone m : milestones) {
            if (m.getQuerySpec() == null) {
                continue;
            } else if (queryBuilderService.requiresIndividualEvaluation(m.getQuerySpec())) {
                UUID catId = m.getCategory() != null ? m.getCategory().getId() : null;
                results.put(m.getId(), queryBuilderService.evaluate(m.getQuerySpec(), userId, catId));
            } else {
                batchable.add(m);
            }
        }

        if (!batchable.isEmpty()) {
            results.putAll(queryBuilderService.evaluateBatch(batchable, userId));
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
            if (userMilestoneSetBonusRepository.existsByUser_IdAndMilestoneSet_Id(userId, set.getId()))
                continue;

            long total = milestoneRepository.countActiveBySetId(set.getId());
            long completed = userMilestoneLinkRepository.countCompletedByUserAndSet(userId, set.getId());
            if (completed < total)
                continue;

            userMilestoneSetBonusRepository.save(UserMilestoneSetBonus.builder()
                    .user(userRepository.getReferenceById(userId))
                    .milestoneSet(set)
                    .claimedAt(Instant.now())
                    .build());

            if (set.getAwardsItem() != null) {
                itemService.awardSystem(
                        userId,
                        set.getAwardsItem().getId(),
                        ItemSource.milestone_set,
                        set.getId().toString(),
                        "Completed milestone set: " + set.getTitle());
            }

            earned.add(set);
        }

        return earned;
    }

    private void awardXp(Long userId, BigDecimal xp) {
        User user = userRepository.findById(userId).orElseThrow();
        BigDecimal oldXp = user.getTotalXp();
        user.setTotalXp(oldXp.add(xp));
        userRepository.save(user);
        levelUpAwardService.processLevelUps(userId, oldXp, xp);
    }

    private boolean isCompleted(Milestone milestone, BigDecimal currentValue) {
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
