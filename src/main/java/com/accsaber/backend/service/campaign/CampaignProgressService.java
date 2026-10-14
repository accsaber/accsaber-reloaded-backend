package com.accsaber.backend.service.campaign;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.projection.UserMapDifficultyBests;
import com.accsaber.backend.model.dto.request.campaign.CampaignFilter;
import com.accsaber.backend.model.dto.response.campaign.BarrierProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignConnectionResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignItemAwardResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignModifierRequirementResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTargetProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CurrentMilestoneResponse;
import com.accsaber.backend.model.dto.response.campaign.UserCampaignResponse;
import com.accsaber.backend.model.entity.campaign.BarrierConditionType;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyModifier;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyTarget;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignScore;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.util.CampaignModifierRule;
import com.accsaber.backend.util.CampaignScoreMetrics;
import com.accsaber.backend.util.ScoreModifierIndex;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignProgressService {

    private static final int MAX_PROGRESS_BULK_IDS = 50;

    private final CampaignRepository campaignRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignDifficultyPathRepository campaignDifficultyPathRepository;
    private final CampaignBarrierAffectedDifficultyRepository barrierAffectedRepository;
    private final UserCampaignRepository userCampaignRepository;
    private final UserCampaignScoreRepository userCampaignScoreRepository;
    private final ScoreRepository scoreRepository;
    private final ScoreModifierLinkRepository scoreModifierLinkRepository;
    private final DuplicateUserService duplicateUserService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final CampaignService campaignService;
    private final CampaignAccess access;
    private final CampaignMapper mapper;

    private record ProgressContext(
            Map<UUID, List<CampaignDifficulty>> difficultiesByCampaign,
            Map<UUID, List<CampaignDifficulty>> barriersByCampaign,
            Map<UUID, List<CampaignConnectionResponse>> prereqsByDifficulty,
            Map<UUID, Map<UUID, Score>> campaignScoreByDifficulty,
            Map<UUID, UserCampaign> userCampaignByCampaign,
            Map<UUID, Set<UUID>> completedByCampaign,
            Map<UUID, Set<UUID>> rewardsPaidByCampaign,
            Map<UUID, Map<UUID, Instant>> completionTimesByCampaign,
            Map<UUID, List<CampaignTagResponse>> tagsByCampaign,
            Map<UUID, List<CampaignItemAwardResponse>> completionItemsByCampaign,
            Map<UUID, List<UUID>> affectedByBarrier,
            Map<UUID, List<CampaignItemAwardResponse>> itemsByNode,
            Map<UUID, List<CampaignModifierRequirementResponse>> modifiersByNode,
            Map<UUID, CampaignModifierRule> modifierRulesByNode,
            Map<UUID, List<CampaignDifficultyTarget>> targetsByNode,
            Map<UUID, Double> complexityByMapDifficulty) {
    }

    public Page<UserCampaignResponse> listUserCampaigns(Long userId, CampaignFilter filter, Long viewerId,
            boolean privileged, Pageable pageable) {
        Long participantId = duplicateUserService.resolvePrimaryUserId(userId);
        Page<CampaignResponse> campaigns = campaignService.findParticipantCampaigns(participantId, filter, viewerId,
                privileged, pageable);
        List<UUID> campaignIds = campaigns.getContent().stream().map(CampaignResponse::getId).toList();
        Map<UUID, UserCampaign> byCampaign = campaignIds.isEmpty()
                ? Map.of()
                : userCampaignRepository.findByUser_IdAndCampaign_IdInAndActiveTrue(participantId, campaignIds)
                        .stream()
                        .collect(Collectors.toMap(uc -> uc.getCampaign().getId(), uc -> uc));
        Map<UUID, Integer> completedByCampaign = CampaignMapper.countMap(campaignIds.isEmpty()
                ? List.<Object[]>of()
                : userCampaignScoreRepository.countActiveByUserAndCampaignIds(participantId, campaignIds));
        return campaigns.map(campaign -> CampaignMapper.toUserCampaignResponse(byCampaign.get(campaign.getId()),
                campaign, completedByCampaign.getOrDefault(campaign.getId(), 0)));
    }

    public CampaignProgressResponse getUserProgress(Long userId, UUID campaignId) {
        return userProgress(userId, access.loadActiveCampaign(campaignId));
    }

    public CampaignProgressResponse getUserProgressBySlug(Long userId, String slug) {
        return userProgress(userId, campaignRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", slug)));
    }

    private CampaignProgressResponse userProgress(Long userId, Campaign campaign) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        if (access.isDraftHiddenFrom(campaign, resolvedUserId, false)) {
            throw new ResourceNotFoundException("Campaign", campaign.getId());
        }
        ProgressContext ctx = loadProgressContext(resolvedUserId, List.of(campaign.getId()));
        return buildProgress(campaign, resolvedUserId, ctx);
    }

    public List<CampaignProgressResponse> getUserProgressBulk(Long userId, List<UUID> campaignIds) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return List.of();
        }
        if (campaignIds.size() > MAX_PROGRESS_BULK_IDS) {
            throw new ValidationException("ids", "max " + MAX_PROGRESS_BULK_IDS + " campaign ids per request");
        }
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        List<Campaign> campaigns = campaignRepository.findByIdInAndActiveTrue(campaignIds);
        if (campaigns.isEmpty()) {
            return List.of();
        }
        Map<UUID, Campaign> campaignById = campaigns.stream()
                .collect(Collectors.toMap(Campaign::getId, c -> c));
        ProgressContext ctx = loadProgressContext(resolvedUserId,
                campaigns.stream().map(Campaign::getId).toList());
        List<CampaignProgressResponse> results = new ArrayList<>(campaignIds.size());
        for (UUID id : campaignIds) {
            Campaign campaign = campaignById.get(id);
            if (campaign == null || access.isDraftHiddenFrom(campaign, resolvedUserId, false)) {
                continue;
            }
            results.add(buildProgress(campaign, resolvedUserId, ctx));
        }
        return results;
    }

    private ProgressContext loadProgressContext(Long resolvedUserId, Collection<UUID> campaignIds) {
        List<CampaignDifficulty> difficulties = campaignDifficultyRepository
                .findActiveWithMapByCampaignIds(campaignIds);
        Map<UUID, List<CampaignDifficulty>> difficultiesByCampaign = difficulties.stream()
                .collect(Collectors.groupingBy(d -> d.getCampaign().getId()));

        List<CampaignDifficulty> barriers = campaignDifficultyRepository
                .findByCampaign_IdInAndBarrierTrueAndActiveTrue(campaignIds);
        Map<UUID, List<CampaignDifficulty>> barriersByCampaign = barriers.stream()
                .collect(Collectors.groupingBy(b -> b.getCampaign().getId()));

        List<CampaignDifficultyPath> paths = campaignDifficultyPathRepository
                .findByCampaignDifficulty_Campaign_IdInAndActiveTrue(campaignIds);
        Map<UUID, List<CampaignConnectionResponse>> prereqsByDifficulty = paths.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCampaignDifficulty().getId(),
                        Collectors.mapping(CampaignMapper::toConnection, Collectors.toList())));

        Map<UUID, UserCampaign> userCampaignByCampaign = userCampaignRepository
                .findByUser_IdAndCampaign_IdInAndActiveTrue(resolvedUserId, campaignIds).stream()
                .collect(Collectors.toMap(uc -> uc.getCampaign().getId(), uc -> uc));

        List<UserCampaignScore> campaignScores = userCampaignScoreRepository
                .findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(resolvedUserId, campaignIds);
        Map<UUID, Set<UUID>> completedByCampaign = campaignScores.stream()
                .collect(Collectors.groupingBy(
                        ucs -> ucs.getCampaign().getId(),
                        Collectors.mapping(ucs -> ucs.getCampaignDifficulty().getId(), Collectors.toSet())));
        Map<UUID, Set<UUID>> rewardsPaidByCampaign = campaignScores.stream()
                .filter(UserCampaignScore::isRewardsPaid)
                .collect(Collectors.groupingBy(
                        ucs -> ucs.getCampaign().getId(),
                        Collectors.mapping(ucs -> ucs.getCampaignDifficulty().getId(), Collectors.toSet())));
        Map<UUID, Map<UUID, Score>> campaignScoreByDifficulty = new HashMap<>();
        Map<UUID, Map<UUID, Instant>> completionTimesByCampaign = new HashMap<>();
        for (UserCampaignScore ucs : campaignScores) {
            Instant completionTime = ucs.getScore() != null
                    ? CampaignScoreMetrics.effectiveTime(ucs.getScore())
                    : ucs.getSubmittedAt();
            completionTimesByCampaign
                    .computeIfAbsent(ucs.getCampaign().getId(), k -> new HashMap<>())
                    .put(ucs.getCampaignDifficulty().getId(), completionTime);
            if (ucs.getScore() == null) {
                continue;
            }
            campaignScoreByDifficulty
                    .computeIfAbsent(ucs.getCampaign().getId(), k -> new HashMap<>())
                    .put(ucs.getCampaignDifficulty().getId(), ucs.getScore());
        }

        Map<UUID, List<CampaignTagResponse>> tagsByCampaign = mapper.loadTagsByCampaignIds(campaignIds);
        Map<UUID, List<CampaignItemAwardResponse>> completionItemsByCampaign = mapper
                .loadCompletionItemsBulk(campaignIds);

        List<UUID> barrierIds = barriers.stream().map(CampaignDifficulty::getId).toList();
        Map<UUID, List<UUID>> affectedByBarrier = barrierIds.isEmpty()
                ? Map.of()
                : barrierAffectedRepository.findByBarrier_IdIn(barrierIds).stream()
                        .collect(Collectors.groupingBy(a -> a.getBarrier().getId(),
                                Collectors.mapping(a -> a.getAffectedDifficulty().getId(), Collectors.toList())));

        List<UUID> nodeIds = new ArrayList<>(difficulties.size() + barrierIds.size());
        difficulties.forEach(d -> nodeIds.add(d.getId()));
        nodeIds.addAll(barrierIds);
        Map<UUID, List<CampaignItemAwardResponse>> itemsByNode = mapper.loadDifficultyItemsBulk(nodeIds);
        Map<UUID, List<CampaignDifficultyTarget>> targetsByNode = mapper.loadTargetsBulk(nodeIds);
        List<CampaignDifficultyModifier> modifierLinks = mapper.loadDifficultyModifierLinks(nodeIds);
        Map<UUID, List<CampaignModifierRequirementResponse>> modifiersByNode = CampaignMapper
                .groupModifierResponses(modifierLinks);
        Map<UUID, CampaignModifierRule> modifierRulesByNode = CampaignModifierRule.byNode(modifierLinks);
        Map<UUID, Double> complexityByMapDifficulty = mapper.loadComplexitiesBulk(difficulties);

        return new ProgressContext(difficultiesByCampaign, barriersByCampaign, prereqsByDifficulty,
                campaignScoreByDifficulty, userCampaignByCampaign, completedByCampaign, rewardsPaidByCampaign,
                completionTimesByCampaign, tagsByCampaign, completionItemsByCampaign, affectedByBarrier, itemsByNode,
                modifiersByNode, modifierRulesByNode, targetsByNode, complexityByMapDifficulty);
    }

    private CampaignProgressResponse buildProgress(Campaign campaign, Long resolvedUserId, ProgressContext ctx) {
        UUID campaignId = campaign.getId();
        List<CampaignDifficulty> difficulties = ctx.difficultiesByCampaign.getOrDefault(campaignId, List.of());
        Set<UUID> completedIds = ctx.completedByCampaign.getOrDefault(campaignId, Set.of());
        Set<UUID> rewardsPaidIds = ctx.rewardsPaidByCampaign.getOrDefault(campaignId, Set.of());
        Set<UUID> pathCompletedIds = campaignEvaluationService.fullyReachedNodeIds(campaignId, resolvedUserId);
        Map<UUID, Score> campaignScores = ctx.campaignScoreByDifficulty.getOrDefault(campaignId, Map.of());
        UserCampaign uc = ctx.userCampaignByCampaign.get(campaignId);
        boolean agnostic = campaign.isProgressionAgnostic();

        Map<UUID, Instant> completionTimes = ctx.completionTimesByCampaign.getOrDefault(campaignId, Map.of());
        Instant since = CampaignUnlockRules.since(uc);
        Map<UUID, List<Score>> rowsByMap = loadProgressRows(uc, difficulties, resolvedUserId, since);
        ScoreModifierIndex scoreModifiers = ScoreModifierIndex.load(
                rowsByMap.values().stream().flatMap(List::stream).toList(),
                scoreModifierLinkRepository::findModifierRows);

        Map<UUID, UserMapDifficultyBests> bestsByNode = new HashMap<>();
        List<CampaignDifficultyProgressResponse> progress = new ArrayList<>(difficulties.size());
        for (CampaignDifficulty d : difficulties) {
            List<CampaignConnectionResponse> prereqs = ctx.prereqsByDifficulty.getOrDefault(d.getId(), List.of());
            CampaignUnlockRules.Window window = CampaignUnlockRules.window(prereqIds(prereqs), d.pathMode(),
                    completionTimes, since, agnostic);
            UserMapDifficultyBests bests = null;
            Score reference = null;
            if (window != null) {
                CampaignModifierRule rule = ctx.modifierRulesByNode.get(d.getId());
                List<Score> rows = rowsByMap.getOrDefault(d.getMapDifficulty().getId(), List.of()).stream()
                        .filter(r -> CampaignUnlockRules.counts(CampaignScoreMetrics.effectiveTime(r), window)
                                && scoreModifiers.satisfies(r.getId(), rule))
                        .toList();
                bests = CampaignScoreMetrics.reduceBests(d.getMapDifficulty().getId(),
                        d.getMapDifficulty().getMaxScore(), rows, scoreModifiers);
                reference = CampaignScoreMetrics.bestMatchingRow(rows,
                        CampaignScoreMetrics.effectiveTargets(d, ctx.targetsByNode.getOrDefault(d.getId(), List.of())),
                        CampaignScoreMetrics.requiresAllTargets(d), scoreModifiers);
            }
            if (bests != null) {
                bestsByNode.put(d.getId(), bests);
            }
            Double userValue = reference != null && d.getRequirementType() != null
                    ? CampaignScoreMetrics.rowValue(reference, d.getRequirementType(), scoreModifiers)
                    : null;
            List<CampaignTargetProgressResponse> targetProgress = toTargetProgress(
                    ctx.targetsByNode.getOrDefault(d.getId(), List.of()), reference, scoreModifiers);
            Score score = campaignScores.get(d.getId());
            Integer userScore = score != null ? score.getScore() : null;
            progress.add(CampaignDifficultyProgressResponse.builder()
                    .node(CampaignMapper.toCampaignDifficultyResponse(d, new CampaignMapper.NodeAssets(prereqs,
                            ctx.itemsByNode.getOrDefault(d.getId(), List.of()),
                            ctx.modifiersByNode.getOrDefault(d.getId(), List.of()),
                            CampaignMapper.toTargetResponses(ctx.targetsByNode.getOrDefault(d.getId(), List.of())),
                            ctx.complexityByMapDifficulty.get(d.getMapDifficulty().getId()))))
                    .userValue(userValue)
                    .targets(targetProgress)
                    .userScore(userScore)
                    .completed(completedIds.contains(d.getId()))
                    .unlocked(window != null)
                    .pathCompleted(pathCompletedIds.contains(d.getId()))
                    .rewardsEarned(rewardsPaidIds.contains(d.getId()))
                    .build());
        }

        List<BarrierProgressResponse> barrierProgress = buildBarrierProgress(campaignId, ctx, completedIds, agnostic,
                bestsByNode);
        Set<UUID> mapNodeIds = difficulties.stream().map(CampaignDifficulty::getId).collect(Collectors.toSet());
        int completedMapNodes = (int) completedIds.stream().filter(mapNodeIds::contains).count();

        CampaignResponse campaignResponse = mapper.toCampaignResponse(campaign,
                ctx.tagsByCampaign.getOrDefault(campaignId, List.of()), difficulties.size(),
                ctx.completionItemsByCampaign.getOrDefault(campaignId, List.of()));

        return CampaignProgressResponse.builder()
                .id(uc != null ? uc.getId() : null)
                .campaign(campaignResponse)
                .progressStatus(uc != null ? uc.getStatus() : null)
                .startedAt(uc != null ? uc.getStartedAt() : null)
                .completedAt(uc != null ? uc.getCompletedAt() : null)
                .completedDifficulties(completedMapNodes)
                .currentMilestone(furthestReachedMilestone(difficulties, ctx.prereqsByDifficulty, pathCompletedIds))
                .difficulties(progress)
                .barriers(barrierProgress)
                .build();
    }

    private static List<UUID> prereqIds(List<CampaignConnectionResponse> prereqs) {
        return prereqs.stream().map(CampaignConnectionResponse::getComesFromCampaignDifficultyId).toList();
    }

    private static CurrentMilestoneResponse furthestReachedMilestone(List<CampaignDifficulty> difficulties,
            Map<UUID, List<CampaignConnectionResponse>> prereqsByDifficulty, Set<UUID> pathCompletedIds) {
        Map<UUID, List<UUID>> prereqs = new HashMap<>();
        prereqsByDifficulty.forEach((nodeId, connections) -> prereqs.put(nodeId, prereqIds(connections)));
        Map<UUID, Integer> depths = new HashMap<>();
        CampaignDifficulty furthest = null;
        int bestDepth = -1;
        for (CampaignDifficulty d : difficulties) {
            String label = d.getCheckpointLabel();
            if (label == null || label.isBlank() || !pathCompletedIds.contains(d.getId())) {
                continue;
            }
            int depth = nodeDepth(d.getId(), prereqs, depths);
            if (depth > bestDepth) {
                bestDepth = depth;
                furthest = d;
            }
        }
        return furthest == null ? null
                : CurrentMilestoneResponse.builder()
                        .nodeId(furthest.getId())
                        .label(furthest.getCheckpointLabel())
                        .depth(bestDepth)
                        .build();
    }

    private static int nodeDepth(UUID id, Map<UUID, List<UUID>> prereqs, Map<UUID, Integer> memo) {
        Integer cached = memo.get(id);
        if (cached != null) {
            return cached;
        }
        memo.put(id, 0);
        int max = 0;
        for (UUID p : prereqs.getOrDefault(id, List.of())) {
            max = Math.max(max, 1 + nodeDepth(p, prereqs, memo));
        }
        memo.put(id, max);
        return max;
    }

    private List<BarrierProgressResponse> buildBarrierProgress(UUID campaignId, ProgressContext ctx,
            Set<UUID> completedIds, boolean agnostic,
            Map<UUID, UserMapDifficultyBests> bestsByNode) {
        List<CampaignDifficulty> barriers = ctx.barriersByCampaign.getOrDefault(campaignId, List.of());
        if (barriers.isEmpty()) {
            return List.of();
        }
        List<BarrierProgressResponse> result = new ArrayList<>(barriers.size());
        for (CampaignDifficulty b : barriers) {
            List<CampaignConnectionResponse> prereqs = ctx.prereqsByDifficulty.getOrDefault(b.getId(), List.of());
            boolean unlocked = agnostic
                    || CampaignUnlockRules.prereqsCompleted(prereqIds(prereqs), b.pathMode(), completedIds);
            List<UUID> affected = ctx.affectedByBarrier.getOrDefault(b.getId(), List.of());
            result.add(BarrierProgressResponse.builder()
                    .barrier(CampaignMapper.toBarrierResponse(b, prereqs, affected,
                            ctx.itemsByNode.getOrDefault(b.getId(), List.of())))
                    .currentValue(computeBarrierCurrentValue(b, affected, completedIds, bestsByNode))
                    .satisfied(completedIds.contains(b.getId()))
                    .unlocked(unlocked)
                    .build());
        }
        return result;
    }

    private Map<UUID, List<Score>> loadProgressRows(UserCampaign uc, List<CampaignDifficulty> difficulties,
            Long resolvedUserId, Instant since) {
        if (uc == null || difficulties.isEmpty()) {
            return Map.of();
        }
        List<UUID> mapDifficultyIds = difficulties.stream()
                .map(d -> d.getMapDifficulty().getId())
                .distinct()
                .toList();
        return scoreRepository.findEligibleCampaignRows(resolvedUserId, mapDifficultyIds, since).stream()
                .collect(Collectors.groupingBy(s -> s.getMapDifficulty().getId()));
    }

    private static List<CampaignTargetProgressResponse> toTargetProgress(List<CampaignDifficultyTarget> targets,
            Score reference, ScoreModifierIndex modifiers) {
        return targets.stream().map(target -> {
            Double userValue = reference != null
                    ? CampaignScoreMetrics.rowValue(reference, target.getRequirementType(), modifiers)
                    : null;
            return CampaignTargetProgressResponse.builder()
                    .target(CampaignMapper.toTargetResponse(target))
                    .userValue(userValue)
                    .met(CampaignScoreMetrics.satisfies(target, userValue))
                    .build();
        }).toList();
    }

    private static Double computeBarrierCurrentValue(CampaignDifficulty barrier, List<UUID> affected,
            Set<UUID> completedIds, Map<UUID, UserMapDifficultyBests> bestsByNode) {
        BarrierConditionType type = barrier.getBarrierConditionType();
        if (type == null || affected.isEmpty()) {
            return null;
        }
        if (type == BarrierConditionType.COMPLETION_COUNT) {
            return (double) (affected.stream().filter(completedIds::contains).count());
        }
        if (type == BarrierConditionType.FC) {
            return (double) (affected.stream()
                    .map(bestsByNode::get)
                    .filter(bests -> bests != null && bests.hasFullCombo())
                    .count());
        }
        if (type == BarrierConditionType.PASS) {
            return (double) (affected.stream()
                    .map(bestsByNode::get)
                    .filter(bests -> bests != null && bests.hasNoNfPass())
                    .count());
        }
        List<Double> values = new ArrayList<>(affected.size());
        for (UUID nodeId : affected) {
            UserMapDifficultyBests bests = bestsByNode.get(nodeId);
            if (bests == null) {
                return null;
            }
            Double v = CampaignScoreMetrics.barrierMetric(bests, type);
            if (v == null) {
                return null;
            }
            values.add(v);
        }
        if (values.isEmpty()) {
            return null;
        }
        return CampaignScoreMetrics.isMaxAggregate(type)
                ? CampaignScoreMetrics.max(values)
                : CampaignScoreMetrics.average(values);
    }
}
