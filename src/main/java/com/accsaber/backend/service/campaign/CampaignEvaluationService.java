package com.accsaber.backend.service.campaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.projection.UserMapDifficultyBests;
import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignBarrierAffectedDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionItem;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyItem;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignScore;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignCompletionItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;
import com.accsaber.backend.model.event.CampaignCompletedEvent;
import com.accsaber.backend.service.mission.MissionProgressService;
import com.accsaber.backend.util.CampaignScoreMetrics;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignEvaluationService {

    private final UserCampaignRepository userCampaignRepository;
    private final UserCampaignScoreRepository userCampaignScoreRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignDifficultyPathRepository campaignDifficultyPathRepository;
    private final CampaignBarrierAffectedDifficultyRepository barrierAffectedRepository;
    private final CampaignDifficultyItemRepository campaignDifficultyItemRepository;
    private final CampaignCompletionItemRepository campaignCompletionItemRepository;
    private final ScoreRepository scoreRepository;
    private final LevelUpAwardService levelUpAwardService;
    private final ItemService itemService;
    private final MissionProgressService missionProgressService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void evaluateAfterScore(Long userId, Score score) {
        if (score == null || score.isPartial() || score.getMapDifficulty() == null) {
            return;
        }
        UUID mapDifficultyId = score.getMapDifficulty().getId();
        List<UserCampaign> inProgress = userCampaignRepository
                .findByUser_IdAndStatusAndActiveTrue(userId, UserCampaignStatus.IN_PROGRESS);
        if (inProgress.isEmpty()) {
            return;
        }

        Instant scoreTime = effectiveTime(score);
        for (UserCampaign uc : inProgress) {
            Campaign campaign = uc.getCampaign();
            if (!campaign.isActive() || campaign.getStatus() == CampaignStatus.DRAFT) {
                continue;
            }
            CampaignDifficulty difficulty = campaignDifficultyRepository
                    .findByCampaign_IdAndMapDifficulty_IdAndActiveTrue(campaign.getId(), mapDifficultyId)
                    .orElse(null);
            if (difficulty == null || difficulty.isBarrier()) {
                continue;
            }
            if (scoreTime != null && uc.getStartedAt() != null && scoreTime.isBefore(uc.getStartedAt())) {
                continue;
            }
            Graph graph = loadGraph(campaign.getId());
            Set<UUID> completedIds = loadCompletedIds(uc.getUser().getId(), campaign.getId());
            boolean alreadyCompleted = completedIds.contains(difficulty.getId());
            if (meetsRequirement(difficulty, score)
                    && (alreadyCompleted || campaign.isProgressionAgnostic()
                            || prereqsCompleted(difficulty.getId(), graph, completedIds))) {
                recordQualifyingScore(uc, difficulty, score, graph, completedIds);
                completedIds.add(difficulty.getId());
            }
            sweepBarriers(uc, graph, completedIds);
            sweepMilestonePayouts(uc, graph, completedIds);
            evaluateCampaignCompletion(uc, graph, completedIds);
        }
    }

    @Transactional
    public void applyCuratedTransition(UUID campaignId) {
        List<CampaignDifficulty> difficulties = campaignDifficultyRepository
                .findByCampaign_IdAndActiveTrue(campaignId);
        if (difficulties.isEmpty()) {
            return;
        }
        Graph graph = graphFromDifficulties(campaignId, difficulties);
        Map<UUID, CampaignDifficulty> byId = graph.byId;

        List<UserCampaignScore> unpaid = userCampaignScoreRepository
                .findByCampaign_IdAndActiveTrueAndRewardsPaidFalse(campaignId);
        Map<Long, Set<UUID>> completedByUser = new HashMap<>();
        Set<Long> touchedUsers = new HashSet<>();
        for (UserCampaignScore ucs : unpaid) {
            CampaignDifficulty difficulty = byId.get(ucs.getCampaignDifficulty().getId());
            if (difficulty == null) {
                continue;
            }
            Long uid = ucs.getUser().getId();
            if (isMilestone(difficulty)) {
                Set<UUID> completedIds = completedByUser
                        .computeIfAbsent(uid, k -> loadCompletedIds(k, campaignId));
                if (!hasCompletedPath(difficulty.getId(), graph, completedIds)) {
                    continue;
                }
            }
            payDifficultyRewards(uid, difficulty);
            ucs.setRewardsPaid(true);
            userCampaignScoreRepository.save(ucs);
            touchedUsers.add(uid);
        }

        for (Long userId : touchedUsers) {
            UserCampaign uc = userCampaignRepository
                    .findByUser_IdAndCampaign_IdAndActiveTrue(userId, campaignId)
                    .orElse(null);
            if (uc != null) {
                evaluateCampaignCompletion(uc, graph,
                        completedByUser.computeIfAbsent(userId, k -> loadCompletedIds(k, campaignId)));
            }
        }
    }

    @Transactional
    public void recomputeAfterRequirementChange(Campaign campaign, Set<UUID> changedNodeIds) {
        if (changedNodeIds == null || changedNodeIds.isEmpty()) {
            return;
        }
        UUID campaignId = campaign.getId();
        Set<UUID> affected = new HashSet<>(changedNodeIds);
        if (!campaign.isProgressionAgnostic()) {
            affected.addAll(descendantsOf(campaignId, changedNodeIds));
        }
        Set<UUID> barrierGates = barrierAffectedRepository.findByAffectedDifficulty_IdIn(affected).stream()
                .map(a -> a.getBarrier().getId())
                .collect(Collectors.toCollection(HashSet::new));
        if (!barrierGates.isEmpty()) {
            affected.addAll(barrierGates);
            if (!campaign.isProgressionAgnostic()) {
                affected.addAll(descendantsOf(campaignId, barrierGates));
            }
        }

        Set<Long> touchedUsers = new HashSet<>();
        for (UserCampaignScore ucs : userCampaignScoreRepository.findByCampaign_IdAndActiveTrue(campaignId)) {
            if (affected.contains(ucs.getCampaignDifficulty().getId())) {
                ucs.setActive(false);
                userCampaignScoreRepository.save(ucs);
                touchedUsers.add(ucs.getUser().getId());
            }
        }
        if (touchedUsers.isEmpty()) {
            return;
        }

        Graph graph = loadGraph(campaignId);
        for (Long userId : touchedUsers) {
            UserCampaign uc = userCampaignRepository
                    .findByUser_IdAndCampaign_IdAndActiveTrue(userId, campaignId)
                    .orElse(null);
            if (uc == null || uc.getStatus() != UserCampaignStatus.COMPLETED) {
                continue;
            }
            if (!isComplete(campaign, graph, loadCompletedIds(userId, campaignId))) {
                uc.setStatus(UserCampaignStatus.IN_PROGRESS);
                uc.setCompletedAt(null);
                userCampaignRepository.save(uc);
            }
        }
    }

    private Set<UUID> descendantsOf(UUID campaignId, Set<UUID> seeds) {
        List<CampaignDifficultyPath> paths = campaignDifficultyPathRepository
                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaignId);
        Map<UUID, List<UUID>> dependents = new HashMap<>();
        for (CampaignDifficultyPath p : paths) {
            dependents.computeIfAbsent(p.getComesFromCampaignDifficulty().getId(), k -> new ArrayList<>())
                    .add(p.getCampaignDifficulty().getId());
        }
        Set<UUID> result = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>(seeds);
        while (!stack.isEmpty()) {
            for (UUID dependent : dependents.getOrDefault(stack.pop(), Collections.emptyList())) {
                if (result.add(dependent)) {
                    stack.push(dependent);
                }
            }
        }
        return result;
    }

    private boolean isComplete(Campaign campaign, Graph graph, Set<UUID> completedIds) {
        if (graph.byId.isEmpty() || completedIds.isEmpty()) {
            return false;
        }
        if (campaign.getCompletionMode() == CampaignCompletionMode.ALL) {
            for (UUID id : graph.byId.keySet()) {
                if (!graph.barrierIds.contains(id) && !completedIds.contains(id)) {
                    return false;
                }
            }
            return true;
        }
        return graph.terminalId != null && hasCompletedPath(graph.terminalId, graph, completedIds);
    }

    @Transactional
    public void importLegacyScores(Long userId, UUID campaignId) {
        UserCampaign uc = userCampaignRepository.findByUser_IdAndCampaign_IdAndActiveTrue(userId, campaignId)
                .orElse(null);
        if (uc == null || !uc.getCampaign().isLegacy()) {
            return;
        }
        settleCampaignFromCurrentScores(uc);
    }

    @Transactional
    public void evaluateInProgressForUser(Long userId) {
        List<UserCampaign> inProgress = userCampaignRepository
                .findByUser_IdAndStatusAndActiveTrue(userId, UserCampaignStatus.IN_PROGRESS);
        for (UserCampaign uc : inProgress) {
            Campaign campaign = uc.getCampaign();
            if (campaign.isActive() && campaign.getStatus() != CampaignStatus.DRAFT) {
                settleCampaignFromCurrentScores(uc);
            }
        }
    }

    private void settleCampaignFromCurrentScores(UserCampaign uc) {
        Campaign campaign = uc.getCampaign();
        List<CampaignDifficulty> difficulties = campaignDifficultyRepository
                .findActiveWithMapByCampaignId(campaign.getId());
        if (difficulties.isEmpty()) {
            return;
        }
        List<UUID> mapDifficultyIds = difficulties.stream().map(d -> d.getMapDifficulty().getId()).toList();
        Map<UUID, Score> scoreByMapDifficulty = scoreRepository
                .findByUser_IdAndMapDifficulty_IdInAndActiveTrue(uc.getUser().getId(), mapDifficultyIds).stream()
                .collect(Collectors.toMap(s -> s.getMapDifficulty().getId(), s -> s, (a, b) -> a));
        Map<UUID, UserMapDifficultyBests> bestsByMapDifficulty = loadBests(uc, mapDifficultyIds);
        if (scoreByMapDifficulty.isEmpty() || bestsByMapDifficulty.isEmpty()) {
            return;
        }
        Graph graph = loadGraph(campaign.getId());
        boolean agnostic = campaign.isProgressionAgnostic();
        Set<UUID> completedIds = loadCompletedIds(uc.getUser().getId(), campaign.getId());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (CampaignDifficulty difficulty : difficulties) {
                UUID mapDifficultyId = difficulty.getMapDifficulty().getId();
                Score score = scoreByMapDifficulty.get(mapDifficultyId);
                UserMapDifficultyBests bests = bestsByMapDifficulty.get(mapDifficultyId);
                if (score == null || bests == null || !meetsRequirement(difficulty, bests)) {
                    continue;
                }
                boolean alreadyCompleted = completedIds.contains(difficulty.getId());
                if (!alreadyCompleted && !agnostic
                        && !prereqsCompleted(difficulty.getId(), graph, completedIds)) {
                    continue;
                }
                recordQualifyingScore(uc, difficulty, score, graph, completedIds);
                if (!alreadyCompleted) {
                    completedIds.add(difficulty.getId());
                    changed = true;
                }
            }
            int beforeBarrier = completedIds.size();
            sweepBarriers(uc, graph, completedIds);
            if (completedIds.size() > beforeBarrier) {
                changed = true;
            }
            if (agnostic) {
                break;
            }
        }
        sweepMilestonePayouts(uc, graph, completedIds);
        evaluateCampaignCompletion(uc, graph, completedIds);
    }

    private void recordQualifyingScore(UserCampaign uc, CampaignDifficulty difficulty, Score score,
            Graph graph, Set<UUID> completedIdsBefore) {
        UserCampaignScore existing = userCampaignScoreRepository
                .findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(uc.getUser().getId(), difficulty.getId())
                .orElse(null);
        if (existing != null) {
            if (isBetterScore(score, existing.getScore())) {
                existing.setScore(score);
                userCampaignScoreRepository.save(existing);
            }
            return;
        }
        UserCampaignScore record = UserCampaignScore.builder()
                .user(uc.getUser())
                .campaign(uc.getCampaign())
                .campaignDifficulty(difficulty)
                .score(score)
                .submittedAt(Instant.now())
                .build();
        if (uc.getCampaign().getStatus() == CampaignStatus.CURATED) {
            boolean payable;
            if (isMilestone(difficulty)) {
                Set<UUID> after = new HashSet<>(completedIdsBefore);
                after.add(difficulty.getId());
                payable = hasCompletedPath(difficulty.getId(), graph, after);
            } else {
                payable = true;
            }
            if (payable) {
                payDifficultyRewards(uc.getUser().getId(), difficulty);
                record.setRewardsPaid(true);
            }
        }
        userCampaignScoreRepository.save(record);
    }

    private void sweepBarriers(UserCampaign uc, Graph graph, Set<UUID> completedIds) {
        if (graph.barrierIds.isEmpty()) {
            return;
        }
        List<UUID> pending = graph.barrierIds.stream()
                .filter(id -> !completedIds.contains(id))
                .toList();
        if (pending.isEmpty()) {
            return;
        }
        Map<UUID, List<UUID>> affectedByBarrier = new HashMap<>();
        Set<UUID> affectedNodeIds = new HashSet<>();
        for (CampaignBarrierAffectedDifficulty link : barrierAffectedRepository.findByBarrier_IdIn(pending)) {
            UUID barrierId = link.getBarrier().getId();
            UUID nodeId = link.getAffectedDifficulty().getId();
            affectedByBarrier.computeIfAbsent(barrierId, k -> new ArrayList<>()).add(nodeId);
            affectedNodeIds.add(nodeId);
        }
        if (affectedNodeIds.isEmpty()) {
            return;
        }
        Map<UUID, UUID> mapDifficultyByNode = new HashMap<>();
        for (UUID nodeId : affectedNodeIds) {
            CampaignDifficulty node = graph.byId.get(nodeId);
            if (node != null && node.getMapDifficulty() != null) {
                mapDifficultyByNode.put(nodeId, node.getMapDifficulty().getId());
            }
        }
        Map<UUID, UserMapDifficultyBests> bestsByMapDifficulty = loadBests(uc, mapDifficultyByNode.values());
        boolean agnostic = uc.getCampaign().isProgressionAgnostic();

        boolean changed = true;
        while (changed) {
            changed = false;
            for (UUID barrierId : pending) {
                if (completedIds.contains(barrierId)) {
                    continue;
                }
                List<UUID> affected = affectedByBarrier.getOrDefault(barrierId, List.of());
                if (affected.isEmpty()) {
                    continue;
                }
                if (!agnostic && !prereqsCompleted(barrierId, graph, completedIds)) {
                    continue;
                }
                CampaignDifficulty barrier = graph.byId.get(barrierId);
                CampaignPrerequisiteMode mode = graph.modes.getOrDefault(barrierId, CampaignPrerequisiteMode.AND);
                if (barrierBroken(barrier, affected, mode, completedIds, mapDifficultyByNode, bestsByMapDifficulty)) {
                    recordBarrierCompletion(uc, barrier);
                    completedIds.add(barrierId);
                    changed = true;
                }
            }
        }
    }

    private boolean barrierBroken(CampaignDifficulty barrier, List<UUID> affected, CampaignPrerequisiteMode mode,
            Set<UUID> completedIds, Map<UUID, UUID> mapDifficultyByNode,
            Map<UUID, UserMapDifficultyBests> bestsByMapDifficulty) {
        if (barrier == null) {
            return false;
        }
        if (barrier.getBarrierConditionType() == BarrierConditionType.COMPLETION_COUNT) {
            BigDecimal target = barrier.getBarrierConditionValue();
            if (target == null) {
                return false;
            }
            long completed = affected.stream().filter(completedIds::contains).count();
            return BigDecimal.valueOf(completed).compareTo(target) >= 0;
        }
        if (mode == CampaignPrerequisiteMode.OR) {
            for (UUID nodeId : affected) {
                if (!completedIds.contains(nodeId)) {
                    continue;
                }
                UserMapDifficultyBests b = bestsByMapDifficulty.get(mapDifficultyByNode.get(nodeId));
                if (b != null && barrierSatisfied(barrier, List.of(b))) {
                    return true;
                }
            }
            return false;
        }
        if (!completedIds.containsAll(affected)) {
            return false;
        }
        List<UserMapDifficultyBests> bests = new ArrayList<>(affected.size());
        for (UUID nodeId : affected) {
            UserMapDifficultyBests b = bestsByMapDifficulty.get(mapDifficultyByNode.get(nodeId));
            if (b != null) {
                bests.add(b);
            }
        }
        return bests.size() == affected.size() && barrierSatisfied(barrier, bests);
    }

    private Map<UUID, UserMapDifficultyBests> loadBests(UserCampaign uc, Collection<UUID> mapDifficultyIds) {
        if (mapDifficultyIds.isEmpty()) {
            return Map.of();
        }
        Instant since = !uc.getCampaign().isLegacy() && uc.getStartedAt() != null
                ? uc.getStartedAt()
                : Instant.EPOCH;
        return scoreRepository
                .findBestsByUserAndMapDifficulties(uc.getUser().getId(), mapDifficultyIds, since).stream()
                .collect(Collectors.toMap(UserMapDifficultyBests::mapDifficultyId, b -> b));
    }

    private void recordBarrierCompletion(UserCampaign uc, CampaignDifficulty barrier) {
        boolean exists = userCampaignScoreRepository
                .findByUser_IdAndCampaignDifficulty_IdAndActiveTrue(uc.getUser().getId(), barrier.getId())
                .isPresent();
        if (exists) {
            return;
        }
        UserCampaignScore record = UserCampaignScore.builder()
                .user(uc.getUser())
                .campaign(uc.getCampaign())
                .campaignDifficulty(barrier)
                .score(null)
                .submittedAt(Instant.now())
                .build();
        if (uc.getCampaign().getStatus() == CampaignStatus.CURATED) {
            payDifficultyRewards(uc.getUser().getId(), barrier);
            record.setRewardsPaid(true);
        }
        userCampaignScoreRepository.save(record);
    }

    private boolean barrierSatisfied(CampaignDifficulty barrier, List<UserMapDifficultyBests> bests) {
        if (barrier == null || bests.isEmpty()) {
            return false;
        }
        BarrierConditionType type = barrier.getBarrierConditionType();
        if (type == null) {
            return false;
        }
        if (type == BarrierConditionType.FC) {
            return bests.stream().allMatch(UserMapDifficultyBests::hasFullCombo);
        }
        BigDecimal target = barrier.getBarrierConditionValue();
        if (target == null) {
            return false;
        }
        List<BigDecimal> values = new ArrayList<>(bests.size());
        for (UserMapDifficultyBests b : bests) {
            BigDecimal v = CampaignScoreMetrics.barrierMetric(b, type);
            if (v == null) {
                return false;
            }
            values.add(v);
        }
        BigDecimal aggregate = CampaignScoreMetrics.isMaxAggregate(type)
                ? CampaignScoreMetrics.max(values)
                : CampaignScoreMetrics.average(values);
        int cmp = CampaignScoreMetrics.toDisplayPrecision(aggregate, type)
                .compareTo(CampaignScoreMetrics.toDisplayPrecision(target, type));
        return type.isLowerBetter() ? cmp <= 0 : cmp >= 0;
    }

    private void sweepMilestonePayouts(UserCampaign uc, Graph graph, Set<UUID> completedIds) {
        if (uc.getCampaign().getStatus() != CampaignStatus.CURATED) {
            return;
        }
        List<UserCampaignScore> unpaid = userCampaignScoreRepository
                .findByUser_IdAndCampaign_IdAndActiveTrueAndRewardsPaidFalse(uc.getUser().getId(),
                        uc.getCampaign().getId());
        if (unpaid.isEmpty()) {
            return;
        }
        for (UserCampaignScore ucs : unpaid) {
            CampaignDifficulty difficulty = ucs.getCampaignDifficulty();
            if (!isMilestone(difficulty)) {
                continue;
            }
            if (!hasCompletedPath(difficulty.getId(), graph, completedIds)) {
                continue;
            }
            payDifficultyRewards(ucs.getUser().getId(), difficulty);
            ucs.setRewardsPaid(true);
            userCampaignScoreRepository.save(ucs);
        }
    }

    private void evaluateCampaignCompletion(UserCampaign uc, Graph graph, Set<UUID> completedIds) {
        Campaign campaign = uc.getCampaign();
        if (uc.getStatus() == UserCampaignStatus.COMPLETED && uc.isCompletionRewardsPaid()) {
            return;
        }
        if (!isComplete(campaign, graph, completedIds)) {
            return;
        }

        boolean newlyCompleted = uc.getStatus() != UserCampaignStatus.COMPLETED;
        if (newlyCompleted) {
            uc.setStatus(UserCampaignStatus.COMPLETED);
            uc.setCompletedAt(Instant.now());
        }
        if (campaign.getStatus() == CampaignStatus.CURATED && !uc.isCompletionRewardsPaid()) {
            payCompletionRewards(uc.getUser().getId(), campaign);
            uc.setCompletionRewardsPaid(true);
        }
        userCampaignRepository.save(uc);

        if (newlyCompleted) {
            eventPublisher.publishEvent(new CampaignCompletedEvent(uc.getUser().getId(), campaign.getId(),
                    campaign.getStatus(), uc.getCompletedAt()));
        }
    }

    private static boolean isMilestone(CampaignDifficulty difficulty) {
        if (difficulty.isBarrier()) {
            return false;
        }
        String label = difficulty.getCheckpointLabel();
        return label != null && !label.isBlank();
    }

    private Graph loadGraph(UUID campaignId) {
        return graphFromDifficulties(campaignId,
                campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaignId));
    }

    private Graph graphFromDifficulties(UUID campaignId, List<CampaignDifficulty> difficulties) {
        Map<UUID, CampaignDifficulty> byId = new HashMap<>(difficulties.size() * 2);
        Map<UUID, CampaignPrerequisiteMode> modes = new HashMap<>(difficulties.size() * 2);
        Set<UUID> barrierIds = new HashSet<>();
        for (CampaignDifficulty d : difficulties) {
            byId.put(d.getId(), d);
            modes.put(d.getId(), d.getPrerequisiteMode() != null
                    ? d.getPrerequisiteMode()
                    : CampaignPrerequisiteMode.OR);
            if (d.isBarrier()) {
                barrierIds.add(d.getId());
            }
        }
        List<CampaignDifficultyPath> paths = campaignDifficultyPathRepository
                .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaignId);
        Map<UUID, List<UUID>> prereqs = new HashMap<>();
        Set<UUID> withOutgoing = new HashSet<>();
        for (CampaignDifficultyPath p : paths) {
            UUID to = p.getCampaignDifficulty().getId();
            UUID from = p.getComesFromCampaignDifficulty().getId();
            prereqs.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
            withOutgoing.add(from);
        }
        UUID terminalId = null;
        for (UUID id : byId.keySet()) {
            if (!withOutgoing.contains(id)) {
                if (terminalId != null) {
                    terminalId = null;
                    break;
                }
                terminalId = id;
            }
        }
        if (terminalId != null && barrierIds.contains(terminalId)) {
            terminalId = null;
        }
        return new Graph(byId, prereqs, modes, barrierIds, terminalId);
    }

    private Set<UUID> loadCompletedIds(Long userId, UUID campaignId) {
        return userCampaignScoreRepository.findByUser_IdAndCampaign_IdAndActiveTrue(userId, campaignId).stream()
                .map(ucs -> ucs.getCampaignDifficulty().getId())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static boolean prereqsCompleted(UUID nodeId, Graph graph, Set<UUID> completedIds) {
        List<UUID> ps = graph.prereqs.getOrDefault(nodeId, Collections.emptyList());
        if (ps.isEmpty()) {
            return true;
        }
        CampaignPrerequisiteMode mode = graph.modes.getOrDefault(nodeId, CampaignPrerequisiteMode.OR);
        if (mode == CampaignPrerequisiteMode.AND) {
            return completedIds.containsAll(ps);
        }
        for (UUID p : ps) {
            if (completedIds.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCompletedPath(UUID targetId, Graph graph, Set<UUID> completedIds) {
        if (!completedIds.contains(targetId)) {
            return false;
        }
        return computeFullyReached(graph, completedIds).contains(targetId);
    }

    private static Set<UUID> computeFullyReached(Graph graph, Set<UUID> completedIds) {
        Set<UUID> fully = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (UUID node : completedIds) {
                if (fully.contains(node)) {
                    continue;
                }
                List<UUID> ps = graph.prereqs.getOrDefault(node, Collections.emptyList());
                if (ps.isEmpty()) {
                    fully.add(node);
                    changed = true;
                    continue;
                }
                CampaignPrerequisiteMode mode = graph.modes.getOrDefault(node, CampaignPrerequisiteMode.OR);
                if (satisfiedByFully(ps, mode, fully)) {
                    fully.add(node);
                    changed = true;
                }
            }
        }
        return fully;
    }

    private static boolean satisfiedByFully(List<UUID> ps, CampaignPrerequisiteMode mode, Set<UUID> fully) {
        if (mode == CampaignPrerequisiteMode.AND) {
            for (UUID p : ps) {
                if (!fully.contains(p)) {
                    return false;
                }
            }
            return true;
        }
        for (UUID p : ps) {
            if (fully.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private record Graph(
            Map<UUID, CampaignDifficulty> byId,
            Map<UUID, List<UUID>> prereqs,
            Map<UUID, CampaignPrerequisiteMode> modes,
            Set<UUID> barrierIds,
            UUID terminalId) {
    }

    private void payDifficultyRewards(Long userId, CampaignDifficulty difficulty) {
        if (difficulty.getXp() != null && difficulty.getXp().signum() > 0) {
            levelUpAwardService.addCampaignXp(userId, difficulty.getXp());
            missionProgressService.creditXp(userId, difficulty.getXp());
        }
        List<CampaignDifficultyItem> items = campaignDifficultyItemRepository
                .findByCampaignDifficulty_Id(difficulty.getId());
        for (CampaignDifficultyItem item : items) {
            itemService.awardSystem(userId, item.getItem().getId(), ItemSource.campaign_difficulty,
                    difficulty.getId().toString(),
                    "Campaign difficulty cleared: " + difficulty.getCampaign().getName());
        }
    }

    private void payCompletionRewards(Long userId, Campaign campaign) {
        if (campaign.getCompletionXp() != null && campaign.getCompletionXp().signum() > 0) {
            levelUpAwardService.addCampaignXp(userId, campaign.getCompletionXp());
            missionProgressService.creditXp(userId, campaign.getCompletionXp());
        }
        List<CampaignCompletionItem> items = campaignCompletionItemRepository.findByCampaign_Id(campaign.getId());
        for (CampaignCompletionItem item : items) {
            itemService.awardSystem(userId, item.getItem().getId(), ItemSource.campaign_completion,
                    campaign.getId().toString(), "Campaign completed: " + campaign.getName());
        }
    }

    private boolean meetsRequirement(CampaignDifficulty difficulty, Score score) {
        return requirementMet(difficulty, CampaignScoreMetrics.requirementValue(score, difficulty.getRequirementType()));
    }

    private boolean meetsRequirement(CampaignDifficulty difficulty, UserMapDifficultyBests bests) {
        return requirementMet(difficulty, CampaignScoreMetrics.requirementValue(bests, difficulty.getRequirementType()));
    }

    private boolean requirementMet(CampaignDifficulty difficulty, BigDecimal value) {
        if (value == null) {
            return false;
        }
        CampaignRequirementType type = difficulty.getRequirementType();
        int cmp = CampaignScoreMetrics.toDisplayPrecision(value, type)
                .compareTo(CampaignScoreMetrics.toDisplayPrecision(difficulty.getRequirementValue(), type));
        return type == CampaignRequirementType.RANK ? cmp <= 0 : cmp >= 0;
    }

    private boolean isBetterScore(Score candidate, Score current) {
        if (current == null) {
            return true;
        }
        if (candidate.getAp() == null) {
            return false;
        }
        if (current.getAp() == null) {
            return true;
        }
        return candidate.getAp().compareTo(current.getAp()) > 0;
    }

    private Instant effectiveTime(Score score) {
        if (score.getTimeSet() != null) {
            return score.getTimeSet();
        }
        return score.getCreatedAt();
    }
}
