package com.accsaber.backend.service.milestone;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetGroupRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.CreatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdateMilestoneSetLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.response.milestone.MilestoneCompletionResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneHolderResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetGroupResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetLinkResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.dto.response.milestone.PrerequisiteLinkResponse;
import com.accsaber.backend.model.dto.response.milestone.UserMilestoneProgressResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyMilestoneLink;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneCompletionStats;
import com.accsaber.backend.model.entity.milestone.MilestonePrerequisiteLink;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneSetGroup;
import com.accsaber.backend.model.entity.milestone.MilestoneSetLink;
import com.accsaber.backend.model.entity.milestone.MilestoneStatus;
import com.accsaber.backend.model.entity.milestone.MilestoneTier;
import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyMilestoneLinkRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.milestone.MilestoneCompletionStatsRepository;
import com.accsaber.backend.repository.milestone.MilestonePrerequisiteLinkRepository;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetGroupRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetLinkRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

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
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyMilestoneLinkRepository mapDifficultyMilestoneLinkRepository;
    private final MilestonePrerequisiteLinkRepository prerequisiteLinkRepository;
    private final MilestoneSetGroupRepository setGroupRepository;
    private final MilestoneSetLinkRepository setLinkRepository;
    private final MilestoneEvaluationService milestoneEvaluationService;
    private final MilestoneQueryBuilderService queryBuilderService;
    private final DuplicateUserService duplicateUserService;

    public Page<MilestoneResponse> findAllActive(UUID setId, UUID categoryId, String type, Pageable pageable) {
        return findAllByStatus(setId, categoryId, type, MilestoneStatus.ACTIVE, pageable);
    }

    public Page<MilestoneResponse> findAllByStatus(UUID setId, UUID categoryId, String type,
            MilestoneStatus status, Pageable pageable) {
        Page<Milestone> milestones = milestoneRepository.findAllActiveFiltered(setId, categoryId, type, status,
                pageable);

        Map<UUID, MilestoneCompletionStats> statsMap = completionStatsRepository.findAll().stream()
                .collect(Collectors.toMap(MilestoneCompletionStats::getMilestoneId, Function.identity()));

        return milestones.map(m -> toResponse(m, statsMap.get(m.getId())));
    }

    public MilestoneResponse findById(UUID id) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrueAndStatusActive(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
        MilestoneCompletionStats stats = completionStatsRepository.findByMilestoneId(id).orElse(null);
        return toResponse(milestone, stats);
    }

    public List<MilestoneResponse> findBySet(UUID setId) {
        milestoneSetRepository.findByIdAndActiveTrue(setId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSet", setId));
        List<Milestone> milestones = milestoneRepository.findByMilestoneSet_IdAndActiveTrueAndStatus(setId,
                MilestoneStatus.ACTIVE);
        Map<UUID, MilestoneCompletionStats> statsMap = completionStatsRepository.findAll().stream()
                .collect(Collectors.toMap(MilestoneCompletionStats::getMilestoneId, Function.identity()));
        return milestones.stream().map(m -> toResponse(m, statsMap.get(m.getId()))).toList();
    }

    public List<UserMilestoneProgressResponse> findCompletedByUser(Long userId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        List<UserMilestoneLink> completedLinks = userMilestoneLinkRepository
                .findCompletedByUserWithMilestoneDetails(resolved);
        Map<UUID, MilestoneCompletionStats> statsMap = completionStatsRepository.findAll().stream()
                .collect(Collectors.toMap(MilestoneCompletionStats::getMilestoneId, Function.identity()));

        return completedLinks.stream().map(link -> {
            Milestone m = link.getMilestone();
            MilestoneCompletionStats stats = statsMap.get(m.getId());
            return UserMilestoneProgressResponse.builder()
                    .milestoneId(m.getId())
                    .title(m.getTitle())
                    .description(m.getDescription())
                    .type(m.getType())
                    .tier(m.getTier().name())
                    .xp(m.getXp())
                    .targetValue(m.getTargetValue())
                    .progress(link.getProgress())
                    .normalizedProgress(normalizeProgress(link.getProgress(), m.getTargetValue(), m.getComparison()))
                    .completed(true)
                    .completedAt(link.getCompletedAt())
                    .completionPercentage(stats != null ? stats.getCompletionPercentage() : BigDecimal.ZERO)
                    .setId(m.getMilestoneSet().getId())
                    .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                    .build();
        }).toList();
    }

    public List<MilestoneCompletionResponse> findAllCompletionStats(Long userId, String sort) {
        List<Milestone> milestones = milestoneRepository.findByActiveTrueAndStatus(MilestoneStatus.ACTIVE);
        Map<UUID, MilestoneCompletionStats> statsMap = completionStatsRepository.findAll().stream()
                .collect(Collectors.toMap(MilestoneCompletionStats::getMilestoneId, Function.identity()));

        Map<UUID, UserMilestoneLink> userLinkMap = Map.of();
        if (userId != null) {
            Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
            List<UUID> milestoneIds = milestones.stream().map(Milestone::getId).toList();
            userLinkMap = userMilestoneLinkRepository
                    .findByUserWithScoreDetails(resolved, milestoneIds).stream()
                    .collect(Collectors.toMap(l -> l.getMilestone().getId(), Function.identity()));
        }

        Map<UUID, UserMilestoneLink> finalUserLinkMap = userLinkMap;

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
                .map(m -> toCompletionResponse(m, statsMap.get(m.getId()), finalUserLinkMap.get(m.getId())))
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
                .map(s -> toSetResponse(s, null));
    }

    public Page<MilestoneSetResponse> findAllSets(Long userId, Pageable pageable) {
        Page<MilestoneSet> sets = milestoneSetRepository.findByActiveTrueWithActiveMilestones(pageable);

        Map<UUID, BigDecimal> userPercentages = Map.of();
        if (userId != null) {
            Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
            Map<UUID, Long> totalPerSet = milestoneRepository.countActiveGroupedBySetId().stream()
                    .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
            Map<UUID, Long> completedPerSet = userMilestoneLinkRepository
                    .countCompletedByUserGroupedBySet(resolved).stream()
                    .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

            Map<UUID, BigDecimal> pcts = new java.util.HashMap<>();
            for (var entry : totalPerSet.entrySet()) {
                long total = entry.getValue();
                long completed = completedPerSet.getOrDefault(entry.getKey(), 0L);
                if (total > 0) {
                    pcts.put(entry.getKey(),
                            BigDecimal.valueOf(completed)
                                    .divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(2, java.math.RoundingMode.HALF_UP));
                }
            }
            userPercentages = pcts;
        }

        Map<UUID, BigDecimal> finalPcts = userPercentages;
        return sets.map(s -> toSetResponse(s, finalPcts.get(s.getId())));
    }

    public Page<UserMilestoneProgressResponse> findUserProgress(Long userId, Pageable pageable) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        Page<Milestone> allActive = milestoneRepository.findAllActiveFiltered(null, null, null, MilestoneStatus.ACTIVE,
                pageable);
        List<UserMilestoneLink> userLinks = userMilestoneLinkRepository.findByUser_Id(resolved);
        Map<UUID, UserMilestoneLink> linkMap = userLinks.stream()
                .collect(Collectors.toMap(l -> l.getMilestone().getId(), Function.identity()));
        Map<UUID, MilestoneCompletionStats> statsMap = completionStatsRepository.findAll().stream()
                .collect(Collectors.toMap(MilestoneCompletionStats::getMilestoneId, Function.identity()));

        return allActive.map(m -> {
            UserMilestoneLink link = linkMap.get(m.getId());
            MilestoneCompletionStats stats = statsMap.get(m.getId());

            BigDecimal rawProgress = link != null ? link.getProgress() : null;
            return UserMilestoneProgressResponse.builder()
                    .milestoneId(m.getId())
                    .title(m.getTitle())
                    .description(m.getDescription())
                    .type(m.getType())
                    .tier(m.getTier().name())
                    .xp(m.getXp())
                    .targetValue(m.getTargetValue())
                    .progress(rawProgress)
                    .normalizedProgress(normalizeProgress(rawProgress, m.getTargetValue(), m.getComparison()))
                    .completed(link != null && link.isCompleted())
                    .completedAt(link != null ? link.getCompletedAt() : null)
                    .completionPercentage(stats != null ? stats.getCompletionPercentage() : BigDecimal.ZERO)
                    .setId(m.getMilestoneSet().getId())
                    .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                    .build();
        });
    }

    public Page<MilestoneHolderResponse> findMilestoneHolders(UUID milestoneId, Pageable pageable) {
        milestoneRepository.findByIdAndActiveTrueAndStatusActive(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        return userMilestoneLinkRepository.findCompletedUsersByMilestoneId(milestoneId, pageable)
                .map(link -> {
                    var user = link.getUser();
                    return MilestoneHolderResponse.builder()
                            .userId(user.getId())
                            .name(user.getName())
                            .avatarUrl(user.getAvatarUrl())
                            .country(user.getCountry())
                            .completedAt(link.getCompletedAt())
                            .build();
                });
    }

    @Transactional
    public MilestoneSetResponse createSet(CreateMilestoneSetRequest request) {
        MilestoneSet set = MilestoneSet.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .setBonusXp(request.getSetBonusXp() != null ? request.getSetBonusXp() : BigDecimal.ZERO)
                .build();
        return toSetResponse(milestoneSetRepository.save(set), null);
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
                .xp(request.getXp())
                .querySpec(request.getQuerySpec())
                .targetValue(request.getTargetValue())
                .comparison(request.getComparison() != null ? request.getComparison() : "GTE")
                .blExclusive(request.isBlExclusive())
                .build();
        Milestone saved = milestoneRepository.save(milestone);

        createMapDifficultyLinks(saved, request.getMapDifficultyIds());

        return toResponse(saved, null);
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
        return toResponse(saved, null);
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
        return saved.stream().map(m -> toResponse(m, null)).toList();
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
        if (request.getTargetValue() != null) {
            milestone.setTargetValue(request.getTargetValue());
        }
        if (request.getComparison() != null) {
            milestone.setComparison(request.getComparison());
        }
        Milestone saved = milestoneRepository.save(milestone);
        MilestoneCompletionStats stats = completionStatsRepository.findByMilestoneId(id).orElse(null);
        return toResponse(saved, stats);
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
    public void backfillMilestone(UUID milestoneId) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrueEager(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        List<Long> userIds = userRepository.findByActiveTrue().stream()
                .map(User::getId)
                .toList();
        log.info("Backfill started for milestone '{}' ({}) — {} users", milestone.getTitle(), milestoneId, userIds.size());
        int processed = 0;
        for (Long userId : userIds) {
            try {
                milestoneEvaluationService.evaluateSingleMilestoneForUser(userId, milestone);
            } catch (Exception e) {
                log.error("Backfill failed for user {} on milestone {}: {}", userId, milestoneId, e.getMessage());
            }
            processed++;
            if (processed % 10000 == 0) {
                log.info("Backfill milestone '{}': {}/{} users processed", milestone.getTitle(), processed, userIds.size());
            }
        }
        log.info("Backfill complete for milestone '{}' ({}) — {} users processed", milestone.getTitle(), milestoneId, processed);
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void backfillAllMilestones() {
        List<Long> userIds = userRepository.findByActiveTrueOrderByTotalXpDesc().stream()
                .map(User::getId)
                .toList();
        log.info("Bulk milestone backfill started — {} users (ordered by XP)", userIds.size());
        int processed = 0;
        int totalCompleted = 0;
        for (Long userId : userIds) {
            try {
                var evaluation = milestoneEvaluationService.evaluateAllForUser(userId);
                totalCompleted += evaluation.completedMilestones().size();
                awardMilestoneXp(userId, evaluation);
            } catch (Exception e) {
                log.error("Bulk backfill failed for user {}: {}", userId, e.getMessage(), e);
            }
            processed++;
            if (processed % 10000 == 0) {
                log.info("Bulk backfill: {}/{} users processed, {} milestones completed so far", processed, userIds.size(), totalCompleted);
            }
        }
        log.info("Bulk milestone backfill complete — {} users processed, {} milestones completed", processed, totalCompleted);
    }

    private void awardMilestoneXp(Long userId, MilestoneEvaluationService.EvaluationResult evaluation) {
        BigDecimal xp = BigDecimal.ZERO;
        for (var m : evaluation.completedMilestones()) {
            xp = xp.add(m.getXp() != null ? m.getXp() : BigDecimal.ZERO);
        }
        for (var s : evaluation.completedSets()) {
            xp = xp.add(s.getSetBonusXp() != null ? s.getSetBonusXp() : BigDecimal.ZERO);
        }
        if (xp.compareTo(BigDecimal.ZERO) > 0) {
            userRepository.addXp(userId, xp);
        }
    }

    @Transactional
    public void refreshCompletionStats() {
        completionStatsRepository.refresh();
    }

    @Transactional
    public void addMapDifficultyLinks(UUID milestoneId, List<UUID> mapDifficultyIds) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrue(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        createMapDifficultyLinks(milestone, mapDifficultyIds);
    }

    @Transactional
    public void removeMapDifficultyLinks(UUID milestoneId, List<UUID> mapDifficultyIds) {
        milestoneRepository.findByIdAndActiveTrue(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        mapDifficultyMilestoneLinkRepository.deleteByMilestone_IdAndMapDifficulty_IdIn(milestoneId, mapDifficultyIds);
    }

    @Transactional
    public PrerequisiteLinkResponse createPrerequisiteLink(CreatePrerequisiteLinkRequest request) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrue(request.getMilestoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", request.getMilestoneId()));
        Milestone prerequisite = milestoneRepository.findByIdAndActiveTrue(request.getPrerequisiteMilestoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", request.getPrerequisiteMilestoneId()));
        if (prerequisiteLinkRepository.existsByMilestone_IdAndPrerequisiteMilestone_IdAndActiveTrue(
                milestone.getId(), prerequisite.getId())) {
            throw new ConflictException("Prerequisite link already exists");
        }
        MilestonePrerequisiteLink link = MilestonePrerequisiteLink.builder()
                .milestone(milestone)
                .prerequisiteMilestone(prerequisite)
                .blocker(request.isBlocker())
                .build();
        return toPrerequisiteLinkResponse(prerequisiteLinkRepository.save(link));
    }

    @Transactional
    public PrerequisiteLinkResponse updatePrerequisiteLink(UUID linkId, UpdatePrerequisiteLinkRequest request) {
        MilestonePrerequisiteLink link = prerequisiteLinkRepository.findById(linkId)
                .filter(MilestonePrerequisiteLink::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("PrerequisiteLink", linkId));
        link.setBlocker(request.getBlocker());
        return toPrerequisiteLinkResponse(prerequisiteLinkRepository.save(link));
    }

    @Transactional
    public void deactivatePrerequisiteLink(UUID linkId) {
        MilestonePrerequisiteLink link = prerequisiteLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("PrerequisiteLink", linkId));
        link.setActive(false);
        prerequisiteLinkRepository.save(link);
    }

    public List<PrerequisiteLinkResponse> findPrerequisitesByMilestone(UUID milestoneId) {
        return prerequisiteLinkRepository.findByMilestone_IdAndActiveTrue(milestoneId).stream()
                .map(this::toPrerequisiteLinkResponse)
                .toList();
    }

    public List<PrerequisiteLinkResponse> findPrerequisiteLinksBySet(UUID setId) {
        return prerequisiteLinkRepository.findBySetIdWithPrerequisites(setId).stream()
                .map(this::toPrerequisiteLinkResponse)
                .toList();
    }

    private PrerequisiteLinkResponse toPrerequisiteLinkResponse(MilestonePrerequisiteLink link) {
        Milestone prereq = link.getPrerequisiteMilestone();
        return PrerequisiteLinkResponse.builder()
                .id(link.getId())
                .milestoneId(link.getMilestone().getId())
                .prerequisiteMilestoneId(prereq.getId())
                .prerequisiteTitle(prereq.getTitle())
                .prerequisiteTier(prereq.getTier().name())
                .blocker(link.isBlocker())
                .createdAt(link.getCreatedAt())
                .build();
    }

    // ---- Milestone Set Groups & Links ----

    public List<MilestoneSetGroupResponse> findAllActiveGroups() {
        return setGroupRepository.findByActiveTrue().stream()
                .map(this::toSetGroupResponse)
                .toList();
    }

    @Transactional
    public MilestoneSetGroupResponse createSetGroup(CreateMilestoneSetGroupRequest request) {
        MilestoneSetGroup group = MilestoneSetGroup.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return toSetGroupResponse(setGroupRepository.save(group));
    }

    @Transactional
    public MilestoneSetGroupResponse updateSetGroup(UUID groupId, CreateMilestoneSetGroupRequest request) {
        MilestoneSetGroup group = setGroupRepository.findByIdAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSetGroup", groupId));
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        return toSetGroupResponse(setGroupRepository.save(group));
    }

    @Transactional
    public void deactivateSetGroup(UUID groupId) {
        MilestoneSetGroup group = setGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSetGroup", groupId));
        group.setActive(false);
        setGroupRepository.save(group);
    }

    @Transactional
    public MilestoneSetLinkResponse createSetLink(CreateMilestoneSetLinkRequest request) {
        MilestoneSetGroup group = setGroupRepository.findByIdAndActiveTrue(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSetGroup", request.getGroupId()));
        MilestoneSet set = milestoneSetRepository.findByIdAndActiveTrue(request.getSetId())
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSet", request.getSetId()));
        if (setLinkRepository.existsByGroup_IdAndMilestoneSet_IdAndActiveTrue(group.getId(), set.getId())) {
            throw new ConflictException("Set link already exists");
        }
        MilestoneSetLink link = MilestoneSetLink.builder()
                .group(group)
                .milestoneSet(set)
                .sortOrder(request.getSortOrder())
                .build();
        return toSetLinkResponse(setLinkRepository.save(link));
    }

    @Transactional
    public MilestoneSetLinkResponse updateSetLink(UUID linkId, UpdateMilestoneSetLinkRequest request) {
        MilestoneSetLink link = setLinkRepository.findById(linkId)
                .filter(MilestoneSetLink::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSetLink", linkId));
        link.setSortOrder(request.getSortOrder());
        return toSetLinkResponse(setLinkRepository.save(link));
    }

    @Transactional
    public void deactivateSetLink(UUID linkId) {
        MilestoneSetLink link = setLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSetLink", linkId));
        link.setActive(false);
        setLinkRepository.save(link);
    }

    public List<MilestoneSetLinkResponse> findSetLinksByGroup(UUID groupId) {
        return setLinkRepository.findByGroupIdWithSets(groupId).stream()
                .map(this::toSetLinkResponse)
                .toList();
    }

    public List<MilestoneSetLinkResponse> findSetLinksBySet(UUID setId) {
        return setLinkRepository.findBySetIdWithGroup(setId).stream()
                .map(this::toSetLinkResponse)
                .toList();
    }

    private MilestoneSetGroupResponse toSetGroupResponse(MilestoneSetGroup group) {
        return MilestoneSetGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .createdAt(group.getCreatedAt())
                .build();
    }

    private MilestoneSetLinkResponse toSetLinkResponse(MilestoneSetLink link) {
        return MilestoneSetLinkResponse.builder()
                .id(link.getId())
                .groupId(link.getGroup().getId())
                .groupName(link.getGroup().getName())
                .setId(link.getMilestoneSet().getId())
                .setTitle(link.getMilestoneSet().getTitle())
                .sortOrder(link.getSortOrder())
                .createdAt(link.getCreatedAt())
                .build();
    }

    private void createMapDifficultyLinks(Milestone milestone, List<UUID> mapDifficultyIds) {
        if (mapDifficultyIds == null || mapDifficultyIds.isEmpty()) {
            return;
        }
        for (UUID mdId : mapDifficultyIds) {
            if (mapDifficultyMilestoneLinkRepository.existsByMapDifficulty_IdAndMilestone_Id(mdId, milestone.getId())) {
                continue;
            }
            MapDifficulty md = mapDifficultyRepository.findByIdAndActiveTrue(mdId)
                    .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mdId));
            MapDifficultyMilestoneLink link = MapDifficultyMilestoneLink.builder()
                    .milestone(milestone)
                    .mapDifficulty(md)
                    .build();
            mapDifficultyMilestoneLinkRepository.save(link);
        }
    }

    private BigDecimal normalizeProgress(BigDecimal progress, BigDecimal targetValue, String comparison) {
        if (progress == null || targetValue == null) {
            return null;
        }
        if ("LTE".equals(comparison)) {
            if (progress.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return targetValue.divide(progress, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        }
        if (targetValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        return progress.divide(targetValue, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
    }

    private MilestoneResponse toResponse(Milestone m, MilestoneCompletionStats stats) {
        return MilestoneResponse.builder()
                .id(m.getId())
                .setId(m.getMilestoneSet().getId())
                .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                .title(m.getTitle())
                .description(m.getDescription())
                .type(m.getType())
                .tier(m.getTier().name())
                .xp(m.getXp())
                .querySpec(m.getQuerySpec())
                .targetValue(m.getTargetValue())
                .comparison(m.getComparison())
                .blExclusive(m.isBlExclusive())
                .status(m.getStatus().name())
                .completionPercentage(stats != null ? stats.getCompletionPercentage() : BigDecimal.ZERO)
                .completions(stats != null ? stats.getCompletions() : 0L)
                .totalPlayers(stats != null ? stats.getTotalPlayers() : 0L)
                .createdAt(m.getCreatedAt())
                .build();
    }

    private MilestoneCompletionResponse toCompletionResponse(Milestone m, MilestoneCompletionStats stats,
            UserMilestoneLink userLink) {
        var builder = MilestoneCompletionResponse.builder()
                .milestoneId(m.getId())
                .title(m.getTitle())
                .description(m.getDescription())
                .type(m.getType())
                .tier(m.getTier().name())
                .xp(m.getXp())
                .targetValue(m.getTargetValue())
                .comparison(m.getComparison())
                .blExclusive(m.isBlExclusive())
                .setId(m.getMilestoneSet().getId())
                .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                .completions(stats != null ? stats.getCompletions() : 0L)
                .totalPlayers(stats != null ? stats.getTotalPlayers() : 0L)
                .completionPercentage(stats != null ? stats.getCompletionPercentage() : BigDecimal.ZERO);

        if (userLink != null) {
            builder.userProgress(userLink.getProgress())
                    .userNormalizedProgress(normalizeProgress(userLink.getProgress(), m.getTargetValue(), m.getComparison()))
                    .userCompleted(userLink.isCompleted())
                    .userCompletedAt(userLink.getCompletedAt());
            Score score = userLink.getAchievedWithScore();
            if (score != null) {
                builder.achievedWithScoreId(score.getId())
                        .score(score.getScore());
                MapDifficulty md = score.getMapDifficulty();
                if (md != null) {
                    builder.maxScore(md.getMaxScore())
                            .difficulty(md.getDifficulty().name());
                    com.accsaber.backend.model.entity.map.Map map = md.getMap();
                    if (map != null) {
                        builder.coverUrl(map.getCoverUrl())
                                .songName(map.getSongName())
                                .songAuthor(map.getSongAuthor())
                                .mapAuthor(map.getMapAuthor());
                    }
                }
            }
        }

        return builder.build();
    }

    private MilestoneSetResponse toSetResponse(MilestoneSet s, BigDecimal userCompletionPercentage) {
        return MilestoneSetResponse.builder()
                .id(s.getId())
                .title(s.getTitle())
                .description(s.getDescription())
                .setBonusXp(s.getSetBonusXp())
                .createdAt(s.getCreatedAt())
                .userCompletionPercentage(userCompletionPercentage)
                .build();
    }
}
