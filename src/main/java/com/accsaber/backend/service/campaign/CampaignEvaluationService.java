package com.accsaber.backend.service.campaign;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionItem;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyItem;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.CampaignPrerequisiteMode;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignScore;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.campaign.CampaignCompletionItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignEvaluationService {

    private final UserCampaignRepository userCampaignRepository;
    private final UserCampaignScoreRepository userCampaignScoreRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignDifficultyPathRepository campaignDifficultyPathRepository;
    private final CampaignDifficultyItemRepository campaignDifficultyItemRepository;
    private final CampaignCompletionItemRepository campaignCompletionItemRepository;
    private final ScoreRepository scoreRepository;
    private final LevelUpAwardService levelUpAwardService;
    private final ItemService itemService;

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
            if (difficulty == null) {
                continue;
            }
            if (scoreTime != null && uc.getStartedAt() != null && scoreTime.isBefore(uc.getStartedAt())) {
                continue;
            }
            if (!meetsRequirement(difficulty, score)) {
                continue;
            }
            Graph graph = loadGraph(campaign.getId());
            Set<UUID> completedIds = loadCompletedIds(uc.getUser().getId(), campaign.getId());
            if (!campaign.isProgressionAgnostic()
                    && !prereqsCompleted(difficulty.getId(), graph, completedIds)) {
                continue;
            }
            recordQualifyingScore(uc, difficulty, score, graph, completedIds);
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
    public void importLegacyScores(Long userId, UUID campaignId) {
        UserCampaign uc = userCampaignRepository.findByUser_IdAndCampaign_IdAndActiveTrue(userId, campaignId)
                .orElse(null);
        if (uc == null) {
            return;
        }
        Campaign campaign = uc.getCampaign();
        if (!campaign.isLegacy()) {
            return;
        }

        List<CampaignDifficulty> difficulties = campaignDifficultyRepository
                .findActiveWithMapByCampaignId(campaignId);
        if (difficulties.isEmpty()) {
            return;
        }
        List<UUID> mapDifficultyIds = difficulties.stream().map(d -> d.getMapDifficulty().getId()).toList();
        Map<UUID, Score> scoreByMapDifficulty = scoreRepository
                .findByUser_IdAndMapDifficulty_IdInAndActiveTrue(userId, mapDifficultyIds).stream()
                .collect(Collectors.toMap(s -> s.getMapDifficulty().getId(), s -> s, (a, b) -> a));
        Graph graph = graphFromDifficulties(campaignId, difficulties);

        boolean agnostic = campaign.isProgressionAgnostic();
        Set<UUID> completedIds = loadCompletedIds(userId, campaignId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (CampaignDifficulty difficulty : difficulties) {
                if (completedIds.contains(difficulty.getId())) {
                    continue;
                }
                Score score = scoreByMapDifficulty.get(difficulty.getMapDifficulty().getId());
                if (score == null || score.isPartial()) {
                    continue;
                }
                if (!meetsRequirement(difficulty, score)) {
                    continue;
                }
                if (!agnostic && !prereqsCompleted(difficulty.getId(), graph, completedIds)) {
                    continue;
                }
                recordQualifyingScore(uc, difficulty, score, graph, completedIds);
                completedIds.add(difficulty.getId());
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
        if (graph.byId.isEmpty() || completedIds.isEmpty()) {
            return;
        }

        boolean complete;
        if (campaign.getCompletionMode() == CampaignCompletionMode.ALL) {
            complete = completedIds.containsAll(graph.byId.keySet());
        } else {
            UUID terminalId = graph.terminalId;
            complete = terminalId != null && hasCompletedPath(terminalId, graph, completedIds);
        }
        if (!complete) {
            return;
        }

        if (uc.getStatus() != UserCampaignStatus.COMPLETED) {
            uc.setStatus(UserCampaignStatus.COMPLETED);
            uc.setCompletedAt(Instant.now());
        }
        if (campaign.getStatus() == CampaignStatus.CURATED && !uc.isCompletionRewardsPaid()) {
            payCompletionRewards(uc.getUser().getId(), campaign);
            uc.setCompletionRewardsPaid(true);
        }
        userCampaignRepository.save(uc);
    }

    private static boolean isMilestone(CampaignDifficulty difficulty) {
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
        for (CampaignDifficulty d : difficulties) {
            byId.put(d.getId(), d);
            modes.put(d.getId(), d.getPrerequisiteMode() != null
                    ? d.getPrerequisiteMode()
                    : CampaignPrerequisiteMode.OR);
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
        return new Graph(byId, prereqs, modes, terminalId);
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
            UUID terminalId) {
    }

    private void payDifficultyRewards(Long userId, CampaignDifficulty difficulty) {
        if (difficulty.getXp() != null && difficulty.getXp().signum() > 0) {
            levelUpAwardService.addXp(userId, difficulty.getXp());
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
            levelUpAwardService.addXp(userId, campaign.getCompletionXp());
        }
        List<CampaignCompletionItem> items = campaignCompletionItemRepository.findByCampaign_Id(campaign.getId());
        for (CampaignCompletionItem item : items) {
            itemService.awardSystem(userId, item.getItem().getId(), ItemSource.campaign_completion,
                    campaign.getId().toString(), "Campaign completed: " + campaign.getName());
        }
    }

    private boolean meetsRequirement(CampaignDifficulty difficulty, Score score) {
        BigDecimal value = computeRequirementValue(difficulty, score);
        return value != null && value.compareTo(difficulty.getRequirementValue()) >= 0;
    }

    private BigDecimal computeRequirementValue(CampaignDifficulty difficulty, Score score) {
        return switch (difficulty.getRequirementType()) {
            case ACC -> computeAccuracy(score);
            case AP -> score.getAp();
            case SCORE -> score.getScore() != null ? BigDecimal.valueOf(score.getScore()) : null;
            case STREAK_115 -> score.getStreak115() != null ? BigDecimal.valueOf(score.getStreak115()) : null;
            case FC -> score.getMisses() != null && score.getBadCuts() != null
                    && score.getMisses() == 0 && score.getBadCuts() == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        };
    }

    private BigDecimal computeAccuracy(Score score) {
        MapDifficulty md = score.getMapDifficulty();
        if (md == null || md.getMaxScore() == null || md.getMaxScore() == 0 || score.getScoreNoMods() == null) {
            return null;
        }
        return BigDecimal.valueOf(score.getScoreNoMods())
                .divide(BigDecimal.valueOf(md.getMaxScore()), 6, RoundingMode.HALF_UP);
    }

    private Instant effectiveTime(Score score) {
        if (score.getTimeSet() != null) {
            return score.getTimeSet();
        }
        return score.getCreatedAt();
    }
}
