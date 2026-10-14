package com.accsaber.backend.service.milestone;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetRequest;
import com.accsaber.backend.model.dto.request.milestone.MilestoneRewardRequest;
import com.accsaber.backend.model.dto.response.milestone.MilestoneCompletionResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneHolderResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneRewardResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.dto.response.milestone.UserMilestoneProgressResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.CurveType;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneCompletionStats;
import com.accsaber.backend.model.entity.milestone.MilestoneItem;
import com.accsaber.backend.model.entity.milestone.MilestoneProgressModel;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneSetItem;
import com.accsaber.backend.model.entity.milestone.MilestoneStatus;
import com.accsaber.backend.model.entity.milestone.MilestoneTier;
import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;
import com.accsaber.backend.model.entity.user.UserPinnedMilestone;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.milestone.MilestoneCompletionStatsRepository;
import com.accsaber.backend.repository.milestone.MilestoneItemRepository;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetItemRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.user.UserPinnedMilestoneRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemMapper;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.util.Rounding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final MilestoneSetRepository milestoneSetRepository;
    private final UserMilestoneLinkRepository userMilestoneLinkRepository;
    private final MilestoneCompletionStatsRepository completionStatsRepository;
    private final UserPinnedMilestoneRepository pinnedMilestoneRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final MilestoneEvaluationService milestoneEvaluationService;
    private final MilestoneQueryBuilderService queryBuilderService;
    private final DuplicateUserService duplicateUserService;
    private final ItemRepository itemRepository;
    private final CurveRepository curveRepository;
    private final MilestoneItemRepository milestoneItemRepository;
    private final MilestoneSetItemRepository milestoneSetItemRepository;
    private final MilestoneProgressCalculator progressCalculator;
    private final MilestoneGraphService graphService;

    public Page<MilestoneResponse> findAllActive(UUID setId, UUID categoryId, String type, Pageable pageable) {
        return findAllByStatus(setId, categoryId, type, MilestoneStatus.ACTIVE, pageable);
    }

    public Page<MilestoneResponse> findAllByStatus(UUID setId, UUID categoryId, String type,
            MilestoneStatus status, Pageable pageable) {
        Page<Milestone> milestones = milestoneRepository.findAllActiveFiltered(setId, categoryId, type, status,
                pageable);

        Map<UUID, MilestoneCompletionStats> statsMap = loadCompletionStats();

        Map<UUID, List<MilestoneRewardResponse>> rewards = loadRewards(
                milestones.map(Milestone::getId).toList());
        return milestones.map(m -> toResponse(m, statsMap.get(m.getId()), rewards.get(m.getId())));
    }

    public MilestoneResponse findById(UUID id) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrueAndStatusActive(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
        MilestoneCompletionStats stats = completionStatsRepository.findByMilestoneId(id).orElse(null);
        return toResponse(milestone, stats, loadRewards(List.of(id)).get(id));
    }

    public List<MilestoneResponse> findBySet(UUID setId) {
        milestoneSetRepository.findByIdAndActiveTrue(setId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSet", setId));
        List<Milestone> milestones = milestoneRepository.findByMilestoneSet_IdAndActiveTrueAndStatus(setId,
                MilestoneStatus.ACTIVE);
        Map<UUID, MilestoneCompletionStats> statsMap = loadCompletionStats();
        Map<UUID, List<MilestoneRewardResponse>> rewards = loadRewards(
                milestones.stream().map(Milestone::getId).toList());
        return milestones.stream()
                .map(m -> toResponse(m, statsMap.get(m.getId()), rewards.get(m.getId()))).toList();
    }

    public List<UserMilestoneProgressResponse> findCompletedByUser(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        List<UserMilestoneLink> completedLinks = userMilestoneLinkRepository
                .findCompletedByUserWithMilestoneDetails(resolved);
        Map<UUID, MilestoneCompletionStats> statsMap = loadCompletionStats();

        Map<UUID, List<MilestoneRewardResponse>> rewards = loadRewards(
                completedLinks.stream().map(l -> l.getMilestone().getId()).toList());

        return completedLinks.stream().map(link -> {
            Milestone m = link.getMilestone();
            MilestoneCompletionStats stats = statsMap.get(m.getId());
            return toProgressResponse(m, link, stats, rewards.get(m.getId()));
        }).toList();
    }

    public List<UserMilestoneProgressResponse> findUncompletedByUser(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        List<Milestone> uncompleted = milestoneRepository.findActiveUncompletedForUser(resolved);
        List<UUID> milestoneIds = uncompleted.stream().map(Milestone::getId).toList();
        Map<UUID, UserMilestoneLink> linkMap = userMilestoneLinkRepository
                .findByUser_IdAndMilestone_IdIn(resolved, milestoneIds).stream()
                .collect(Collectors.toMap(l -> l.getMilestone().getId(), Function.identity()));
        Map<UUID, MilestoneCompletionStats> statsMap = loadCompletionStats();

        Map<UUID, List<MilestoneRewardResponse>> rewards = loadRewards(milestoneIds);

        return uncompleted.stream().map(m -> {
            UserMilestoneLink link = linkMap.get(m.getId());
            MilestoneCompletionStats stats = statsMap.get(m.getId());
            return toProgressResponse(m, link, stats, rewards.get(m.getId()));
        }).toList();
    }

    public List<MilestoneCompletionResponse> findAllCompletionStats(Long userId, String sort) {
        List<Milestone> milestones = milestoneRepository.findByActiveTrueAndStatus(MilestoneStatus.ACTIVE);
        Map<UUID, MilestoneCompletionStats> statsMap = loadCompletionStats();

        Map<UUID, UserMilestoneLink> userLinkMap = Map.of();
        if (userId != null) {
            Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
            List<UUID> milestoneIds = milestones.stream().map(Milestone::getId).toList();
            userLinkMap = userMilestoneLinkRepository
                    .findByUserWithScoreDetails(resolved, milestoneIds).stream()
                    .collect(Collectors.toMap(l -> l.getMilestone().getId(), Function.identity()));
        }

        Map<UUID, UserMilestoneLink> finalUserLinkMap = userLinkMap;
        Map<UUID, List<MilestoneRewardResponse>> rewards = loadRewards(
                milestones.stream().map(Milestone::getId).toList());

        Comparator<MilestoneCompletionResponse> comparator = switch (sort) {
            case "completions" -> Comparator.comparing(MilestoneCompletionResponse::getCompletions,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "completedAt" -> Comparator.comparing(MilestoneCompletionResponse::getUserCompletedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "progress" -> Comparator.comparing(MilestoneCompletionResponse::getUserNormalizedProgress,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing((MilestoneCompletionResponse r) -> r.getTier(),
                    Comparator.comparingInt(t -> tierOrder(t)))
                    .thenComparing(MilestoneCompletionResponse::getCompletionPercentage,
                            Comparator.reverseOrder());
        };

        return milestones.stream()
                .map(m -> toCompletionResponse(m, statsMap.get(m.getId()), finalUserLinkMap.get(m.getId()),
                        rewards.get(m.getId())))
                .sorted(comparator)
                .toList();
    }

    private static int tierOrder(String tier) {
        try {
            return MilestoneTier.valueOf(tier).ordinal();
        } catch (IllegalArgumentException e) {
            return Integer.MAX_VALUE;
        }
    }

    public Page<MilestoneSetResponse> findAllSetsAdmin(Pageable pageable) {
        return milestoneSetRepository.findAll(pageable)
                .map(s -> toSetResponse(s, null, null));
    }

    public MilestoneSetResponse findSet(UUID setId) {
        MilestoneSet set = milestoneSetRepository.findByIdAndActiveTrue(setId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSet", setId));
        return toSetResponse(set, null, loadSetRewards(List.of(setId)).get(setId));
    }

    public Page<MilestoneSetResponse> findAllSets(Long userId, Pageable pageable) {
        Page<MilestoneSet> sets = milestoneSetRepository.findByActiveTrueWithActiveMilestones(pageable);

        Map<UUID, Double> userPercentages = Map.of();
        if (userId != null) {
            Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
            Map<UUID, Long> totalPerSet = milestoneRepository.countActiveGroupedBySetId().stream()
                    .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
            Map<UUID, Long> completedPerSet = userMilestoneLinkRepository
                    .countCompletedByUserGroupedBySet(resolved).stream()
                    .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

            Map<UUID, Double> pcts = new java.util.HashMap<>();
            for (var entry : totalPerSet.entrySet()) {
                long total = entry.getValue();
                long completed = completedPerSet.getOrDefault(entry.getKey(), 0L);
                if (total > 0) {
                    pcts.put(entry.getKey(), Rounding
                            .round((Rounding.round((double) (completed) / (double) (total), 4) * (double) (100)), 2));
                }
            }
            userPercentages = pcts;
        }

        Map<UUID, Double> finalPcts = userPercentages;
        Map<UUID, List<MilestoneRewardResponse>> setRewards = loadSetRewards(
                sets.map(MilestoneSet::getId).toList());
        return sets.map(s -> toSetResponse(s, finalPcts.get(s.getId()), setRewards.get(s.getId())));
    }

    public Page<UserMilestoneProgressResponse> findUserProgress(Long userId, Pageable pageable) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Page<Milestone> allActive = milestoneRepository.findAllActiveFiltered(null, null, null, MilestoneStatus.ACTIVE,
                pageable);
        List<UserMilestoneLink> userLinks = userMilestoneLinkRepository.findByUser_Id(resolved);
        Map<UUID, UserMilestoneLink> linkMap = userLinks.stream()
                .collect(Collectors.toMap(l -> l.getMilestone().getId(), Function.identity()));
        Map<UUID, MilestoneCompletionStats> statsMap = loadCompletionStats();

        Map<UUID, List<MilestoneRewardResponse>> rewards = loadRewards(
                allActive.getContent().stream().map(Milestone::getId).toList());

        return allActive.map(m -> {
            UserMilestoneLink link = linkMap.get(m.getId());
            MilestoneCompletionStats stats = statsMap.get(m.getId());
            return toProgressResponse(m, link, stats, rewards.get(m.getId()));
        });
    }

    @org.springframework.cache.annotation.Cacheable(value = "statistics", key = "'milestoneholders:' + #milestoneId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()")
    public Page<MilestoneHolderResponse> findMilestoneHolders(UUID milestoneId, Pageable pageable) {
        milestoneRepository.findByIdAndActiveTrueAndStatusActive(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        return userMilestoneLinkRepository.findCompletedUsersByMilestoneId(milestoneId, pageable);
    }

    @Transactional
    public MilestoneSetResponse createSet(CreateMilestoneSetRequest request) {
        MilestoneSet set = MilestoneSet.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .setBonusXp(request.getSetBonusXp() != null ? request.getSetBonusXp() : 0.0)
                .build();
        MilestoneSet persisted = milestoneSetRepository.save(set);
        replaceSetRewards(persisted, request.getRewards());
        return toSetResponse(persisted, null, loadSetRewards(List.of(persisted.getId())).get(persisted.getId()));
    }

    @Transactional
    public MilestoneSetResponse updateSet(UUID id,
            com.accsaber.backend.model.dto.request.milestone.UpdateMilestoneSetRequest request) {
        MilestoneSet set = milestoneSetRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSet", id));
        if (request.getTitle() != null)
            set.setTitle(request.getTitle());
        if (request.getDescription() != null)
            set.setDescription(request.getDescription());
        if (request.getSetBonusXp() != null)
            set.setSetBonusXp(request.getSetBonusXp());
        MilestoneSet persisted = milestoneSetRepository.save(set);
        replaceSetRewards(persisted, request.getRewards());
        return toSetResponse(persisted, null, loadSetRewards(List.of(persisted.getId())).get(persisted.getId()));
    }

    private Curve loadCurve(UUID curveId) {
        if (curveId == null) {
            return null;
        }
        Curve curve = curveRepository.findById(curveId)
                .orElseThrow(() -> new ResourceNotFoundException("Curve", curveId));
        if (curve.getType() != CurveType.POINT_LOOKUP) {
            throw new ValidationException("progressCurveId must reference a POINT_LOOKUP curve");
        }
        return curve;
    }

    private void assertProgressModelIsUsable(Milestone milestone) {
        if (milestone.getProgressModel() == MilestoneProgressModel.CURVE && milestone.getProgressCurve() == null) {
            throw new ValidationException("progressCurveId is required when progressModel is CURVE");
        }
        if (milestone.getProgressModel() == MilestoneProgressModel.LOG && !"LTE".equals(milestone.getComparison())
                && milestone.getProgressFloor() == null) {
            throw new ValidationException("progressFloor is required for a LOG progress model on a GTE milestone");
        }
    }

    private Map<UUID, Item> loadRewardItems(List<MilestoneRewardRequest> rewards) {
        List<UUID> ids = rewards.stream().map(MilestoneRewardRequest::getItemId).toList();
        if (ids.size() != ids.stream().distinct().count()) {
            throw new ValidationException("A reward item can only be listed once");
        }
        Map<UUID, Item> items = itemRepository.findAllById(ids).stream()
                .filter(Item::isActive)
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Instant now = Instant.now();
        for (UUID id : ids) {
            Item item = items.get(id);
            if (item == null) {
                throw new ResourceNotFoundException("Item", id);
            }
            if (!item.isObtainableAt(now)) {
                throw new ValidationException("'" + item.getName() + "' can no longer be handed out as a reward");
            }
        }
        return items;
    }

    @Transactional
    public MilestoneResponse createMilestone(CreateMilestoneRequest request) {
        MilestoneSet set = milestoneSetRepository.findByIdAndActiveTrue(request.getSetId())
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSet", request.getSetId()));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findByIdAndActiveTrue(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));
        }

        queryBuilderService.validate(request.getQuerySpec());

        Milestone milestone = Milestone.builder()
                .milestoneSet(set)
                .category(category)
                .title(request.getTitle())
                .description(request.getDescription())
                .type(request.getType())
                .tier(request.getTier())
                .iconGroup(request.getIconGroup() != null ? request.getIconGroup() : "GENERAL")
                .xp(request.getXp())
                .querySpec(request.getQuerySpec())
                .targetValue(request.getTargetValue())
                .comparison(request.getComparison() != null ? request.getComparison() : "GTE")
                .positionX(request.getPositionX() != null ? request.getPositionX() : 0.0)
                .positionY(request.getPositionY() != null ? request.getPositionY() : 0.0)
                .progressModel(request.getProgressModel() != null
                        ? request.getProgressModel()
                        : MilestoneProgressModel.LINEAR)
                .progressCurve(loadCurve(request.getProgressCurveId()))
                .progressFloor(request.getProgressFloor())
                .build();
        assertProgressModelIsUsable(milestone);
        Milestone saved = milestoneRepository.save(milestone);

        graphService.createMapDifficultyLinks(saved, request.getMapDifficultyIds());
        replaceMilestoneRewards(saved, request.getRewards());

        return toResponse(saved, null, loadRewards(List.of(saved.getId())).get(saved.getId()));
    }

    @Transactional
    public MilestoneResponse activateMilestone(UUID id) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
        if (milestone.getStatus() == MilestoneStatus.ACTIVE) {
            throw new ConflictException("Milestone is already active: " + id);
        }
        milestone.setStatus(MilestoneStatus.ACTIVE);
        Milestone saved = milestoneRepository.save(milestone);
        return toResponse(saved, null, loadRewards(List.of(saved.getId())).get(saved.getId()));
    }

    @Transactional
    public List<MilestoneResponse> activateMilestones(List<UUID> ids) {
        List<Milestone> milestones = milestoneRepository.findAllActiveByIdIn(ids);
        if (milestones.size() != ids.size()) {
            List<UUID> found = milestones.stream().map(Milestone::getId).toList();
            List<UUID> missing = ids.stream().filter(id -> !found.contains(id)).toList();
            throw new ResourceNotFoundException("Milestones not found: " + missing);
        }
        List<UUID> alreadyActive = milestones.stream()
                .filter(m -> m.getStatus() == MilestoneStatus.ACTIVE)
                .map(Milestone::getId)
                .toList();
        if (!alreadyActive.isEmpty()) {
            throw new ConflictException("Milestones already active: " + alreadyActive);
        }
        for (Milestone milestone : milestones) {
            milestone.setStatus(MilestoneStatus.ACTIVE);
        }
        List<Milestone> saved = milestoneRepository.saveAll(milestones);
        Map<UUID, List<MilestoneRewardResponse>> rewards = loadRewards(
                saved.stream().map(Milestone::getId).toList());
        return saved.stream().map(m -> toResponse(m, null, rewards.get(m.getId()))).toList();
    }

    @Transactional
    public MilestoneResponse updateMilestone(UUID id,
            com.accsaber.backend.model.dto.request.milestone.UpdateMilestoneRequest request) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
        if (request.getTitle() != null) {
            milestone.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            milestone.setDescription(request.getDescription());
        }
        if (request.getQuerySpec() != null) {
            milestone.setQuerySpec(request.getQuerySpec());
        }
        if (request.getXp() != null) {
            milestone.setXp(request.getXp());
        }
        if (request.getTier() != null) {
            milestone.setTier(request.getTier());
        }
        if (request.getIconGroup() != null) {
            milestone.setIconGroup(request.getIconGroup());
        }
        if (request.getTargetValue() != null) {
            milestone.setTargetValue(request.getTargetValue());
        }
        if (request.getComparison() != null) {
            milestone.setComparison(request.getComparison());
        }
        if (request.getPositionX() != null) {
            milestone.setPositionX(request.getPositionX());
        }
        if (request.getPositionY() != null) {
            milestone.setPositionY(request.getPositionY());
        }
        if (request.getProgressModel() != null) {
            milestone.setProgressModel(request.getProgressModel());
        }
        if (request.getProgressCurveId() != null) {
            milestone.setProgressCurve(loadCurve(request.getProgressCurveId()));
        }
        if (request.getProgressFloor() != null) {
            milestone.setProgressFloor(request.getProgressFloor());
        }
        assertProgressModelIsUsable(milestone);
        Milestone saved = milestoneRepository.save(milestone);
        replaceMilestoneRewards(saved, request.getRewards());
        MilestoneCompletionStats stats = completionStatsRepository.findByMilestoneId(id).orElse(null);
        return toResponse(saved, stats, loadRewards(List.of(saved.getId())).get(saved.getId()));
    }

    @Transactional
    public void deactivateMilestone(UUID id) {
        Milestone milestone = milestoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
        milestone.setActive(false);
        milestoneRepository.save(milestone);
    }

    @Transactional
    public void removeMilestone(UUID id) {
        Milestone milestone = milestoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
        milestone.setActive(false);
        milestone.setStatus(MilestoneStatus.DRAFT);
        milestoneRepository.save(milestone);
        userRepository.recalculateTotalXpForAllActiveUsers();
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> backfillMilestone(UUID milestoneId) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrueEager(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        List<Long> userIds = userRepository.findByActiveTrue().stream()
                .map(User::getId)
                .toList();
        log.info("Backfill started for milestone '{}' ({}) - {} users", milestone.getTitle(), milestoneId,
                userIds.size());
        int processed = 0;
        for (Long userId : userIds) {
            try {
                milestoneEvaluationService.evaluateSingleMilestoneForUser(userId, milestone);
            } catch (Exception e) {
                log.error("Backfill failed for user {} on milestone {}: {}", userId, milestoneId, e.getMessage());
            }
            processed++;
            if (processed % 10000 == 0) {
                log.info("Backfill milestone '{}': {}/{} users processed", milestone.getTitle(), processed,
                        userIds.size());
            }
        }
        log.info("Backfill complete for milestone '{}' ({}) - {} users processed", milestone.getTitle(), milestoneId,
                processed);
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> backfillUser(Long userId) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        if (userRepository.findByIdAndActiveTrue(resolvedUserId).isEmpty()) {
            log.warn("Cannot backfill milestones: user {} not found or inactive", resolvedUserId);
            return CompletableFuture.completedFuture(null);
        }
        log.info("Milestone backfill started for user {}", resolvedUserId);
        var evaluation = milestoneEvaluationService.evaluateAllForUser(resolvedUserId);
        log.info("Milestone backfill complete for user {} - {} milestones, {} sets completed",
                resolvedUserId, evaluation.completedMilestones().size(), evaluation.completedSets().size());
        return CompletableFuture.completedFuture(null);
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> backfillAllMilestones() {
        List<Long> userIds = userRepository.findByActiveTrueOrderByTotalXpDesc().stream()
                .map(User::getId)
                .toList();
        log.info("Bulk milestone backfill started - {} users (ordered by XP)", userIds.size());
        int processed = 0;
        int totalCompleted = 0;
        for (Long userId : userIds) {
            try {
                var evaluation = milestoneEvaluationService.evaluateAllForUser(userId);
                totalCompleted += evaluation.completedMilestones().size();
            } catch (Exception e) {
                log.error("Bulk backfill failed for user {}: {}", userId, e.getMessage(), e);
            }
            processed++;
            if (processed % 10000 == 0) {
                log.info("Bulk backfill: {}/{} users processed, {} milestones completed so far", processed,
                        userIds.size(), totalCompleted);
            }
        }
        log.info("Bulk milestone backfill complete - {} users processed, {} milestones completed", processed,
                totalCompleted);
        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    public void refreshCompletionStats() {
        completionStatsRepository.refresh();
    }

    private Map<UUID, MilestoneCompletionStats> loadCompletionStats() {
        return completionStatsRepository.findAll().stream()
                .collect(Collectors.toMap(MilestoneCompletionStats::getMilestoneId, Function.identity()));
    }

    private void replaceMilestoneRewards(Milestone milestone, List<MilestoneRewardRequest> rewards) {
        if (rewards == null) {
            return;
        }
        Map<UUID, Item> items = loadRewardItems(rewards);
        milestoneItemRepository.deleteByMilestone_Id(milestone.getId());
        milestoneItemRepository.saveAll(rewards.stream()
                .map(reward -> MilestoneItem.builder()
                        .id(new MilestoneItem.MilestoneItemId(milestone.getId(), reward.getItemId()))
                        .milestone(milestone)
                        .item(items.get(reward.getItemId()))
                        .quantity(reward.getQuantity())
                        .build())
                .toList());
    }

    private void replaceSetRewards(MilestoneSet set, List<MilestoneRewardRequest> rewards) {
        if (rewards == null) {
            return;
        }
        Map<UUID, Item> items = loadRewardItems(rewards);
        milestoneSetItemRepository.deleteByMilestoneSet_Id(set.getId());
        milestoneSetItemRepository.saveAll(rewards.stream()
                .map(reward -> MilestoneSetItem.builder()
                        .id(new MilestoneSetItem.MilestoneSetItemId(set.getId(), reward.getItemId()))
                        .milestoneSet(set)
                        .item(items.get(reward.getItemId()))
                        .quantity(reward.getQuantity())
                        .build())
                .toList());
    }

    private Map<UUID, List<MilestoneRewardResponse>> loadRewards(Collection<UUID> milestoneIds) {
        if (milestoneIds.isEmpty()) {
            return Map.of();
        }
        return milestoneItemRepository.findByMilestoneIds(milestoneIds).stream()
                .collect(Collectors.groupingBy(link -> link.getMilestone().getId(),
                        Collectors.mapping(link -> ItemMapper.toRewardResponse(link.getItem(), link.getQuantity()),
                                Collectors.toList())));
    }

    private Map<UUID, List<MilestoneRewardResponse>> loadSetRewards(Collection<UUID> setIds) {
        if (setIds.isEmpty()) {
            return Map.of();
        }
        return milestoneSetItemRepository.findBySetIds(setIds).stream()
                .collect(Collectors.groupingBy(link -> link.getMilestoneSet().getId(),
                        Collectors.mapping(link -> ItemMapper.toRewardResponse(link.getItem(), link.getQuantity()),
                                Collectors.toList())));
    }

    private MilestoneResponse toResponse(Milestone m, MilestoneCompletionStats stats,
            List<MilestoneRewardResponse> rewards) {
        return MilestoneResponse.builder()
                .id(m.getId())
                .setId(m.getMilestoneSet().getId())
                .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                .title(m.getTitle())
                .description(m.getDescription())
                .type(m.getType())
                .tier(m.getTier().name())
                .iconGroup(m.getIconGroup())
                .xp(m.getXp())
                .querySpec(m.getQuerySpec())
                .targetValue(m.getTargetValue())
                .comparison(m.getComparison())
                .status(m.getStatus().name())
                .completionPercentage(stats != null ? stats.getCompletionPercentage() : 0.0)
                .completions(stats != null ? stats.getCompletions() : 0L)
                .totalPlayers(stats != null ? stats.getTotalPlayers() : 0L)
                .rewards(rewards)
                .positionX(m.getPositionX())
                .positionY(m.getPositionY())
                .progressModel(m.getProgressModel().name())
                .progressCurveId(m.getProgressCurve() != null ? m.getProgressCurve().getId() : null)
                .progressFloor(m.getProgressFloor())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private UserMilestoneProgressResponse toProgressResponse(Milestone m, UserMilestoneLink link,
            MilestoneCompletionStats stats, List<MilestoneRewardResponse> rewards) {
        Double rawProgress = link != null ? link.getProgress() : null;
        return UserMilestoneProgressResponse.builder()
                .milestoneId(m.getId())
                .title(m.getTitle())
                .description(m.getDescription())
                .type(m.getType())
                .tier(m.getTier().name())
                .iconGroup(m.getIconGroup())
                .querySpec(m.getQuerySpec())
                .xp(m.getXp())
                .targetValue(m.getTargetValue())
                .progress(rawProgress)
                .normalizedProgress(progressCalculator.normalize(m, rawProgress,
                        link != null ? link.getGateFraction() : null))
                .completed(link != null && link.isCompleted())
                .completedAt(link != null ? link.getCompletedAt() : null)
                .completionPercentage(stats != null ? stats.getCompletionPercentage() : 0.0)
                .setId(m.getMilestoneSet().getId())
                .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                .positionX(m.getPositionX())
                .positionY(m.getPositionY())
                .rewards(rewards)
                .build();
    }

    public List<UserMilestoneProgressResponse> findPinnedByUser(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        List<UserPinnedMilestone> pins = pinnedMilestoneRepository.findActiveByUserIdWithMilestoneGraph(resolved);
        if (pins.isEmpty()) {
            return List.of();
        }
        List<UUID> milestoneIds = pins.stream().map(p -> p.getMilestone().getId()).toList();
        Map<UUID, UserMilestoneLink> linkMap = userMilestoneLinkRepository
                .findByUser_IdAndMilestone_IdIn(resolved, milestoneIds).stream()
                .collect(Collectors.toMap(l -> l.getMilestone().getId(), Function.identity()));
        Map<UUID, MilestoneCompletionStats> statsMap = loadCompletionStats();
        Map<UUID, List<MilestoneRewardResponse>> rewards = loadRewards(milestoneIds);
        return pins.stream()
                .map(pin -> toProgressResponse(pin.getMilestone(), linkMap.get(pin.getMilestone().getId()),
                        statsMap.get(pin.getMilestone().getId()), rewards.get(pin.getMilestone().getId())))
                .toList();
    }

    private MilestoneCompletionResponse toCompletionResponse(Milestone m, MilestoneCompletionStats stats,
            UserMilestoneLink userLink, List<MilestoneRewardResponse> rewards) {
        var builder = MilestoneCompletionResponse.builder()
                .milestoneId(m.getId())
                .title(m.getTitle())
                .description(m.getDescription())
                .type(m.getType())
                .tier(m.getTier().name())
                .iconGroup(m.getIconGroup())
                .querySpec(m.getQuerySpec())
                .xp(m.getXp())
                .targetValue(m.getTargetValue())
                .comparison(m.getComparison())
                .setId(m.getMilestoneSet().getId())
                .positionX(m.getPositionX())
                .positionY(m.getPositionY())
                .rewards(rewards)
                .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                .completions(stats != null ? stats.getCompletions() : 0L)
                .totalPlayers(stats != null ? stats.getTotalPlayers() : 0L)
                .completionPercentage(stats != null ? stats.getCompletionPercentage() : 0.0);

        if (userLink != null) {
            builder.userProgress(userLink.getProgress())
                    .userNormalizedProgress(
                            progressCalculator.normalize(m, userLink.getProgress(), userLink.getGateFraction()))
                    .userCompleted(userLink.isCompleted())
                    .userCompletedAt(userLink.getCompletedAt());
            Score score = userLink.getAchievedWithScore();
            if (score != null) {
                builder.achievedWithScoreId(score.getId())
                        .score(score.getScore());
                MapDifficulty md = score.getMapDifficulty();
                if (md != null) {
                    builder.maxScore(md.getMaxScore())
                            .difficulty(md.getDifficulty());
                    com.accsaber.backend.model.entity.map.Map map = md.getMap();
                    if (map != null) {
                        builder.coverUrl(map.getCoverUrl())
                                .cdnCoverUrl(map.getCdnCoverUrl())
                                .songName(map.getSongName())
                                .songSubName(map.getSongSubName())
                                .songAuthor(map.getSongAuthor())
                                .mapAuthor(map.getMapAuthor());
                    }
                }
            }
        }

        return builder.build();
    }

    private MilestoneSetResponse toSetResponse(MilestoneSet s, Double userCompletionPercentage,
            List<MilestoneRewardResponse> rewards) {
        return MilestoneSetResponse.builder()
                .id(s.getId())
                .title(s.getTitle())
                .description(s.getDescription())
                .setBonusXp(s.getSetBonusXp())
                .rewards(rewards)
                .createdAt(s.getCreatedAt())
                .userCompletionPercentage(userCompletionPercentage)
                .build();
    }
}
