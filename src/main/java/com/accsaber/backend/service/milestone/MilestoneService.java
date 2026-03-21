package com.accsaber.backend.service.milestone;

import java.math.BigDecimal;
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
import com.accsaber.backend.model.dto.request.milestone.CreatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.response.milestone.MilestoneCompletionResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.PrerequisiteLinkResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.dto.response.milestone.UserMilestoneProgressResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyMilestoneLink;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneCompletionStats;
import com.accsaber.backend.model.entity.milestone.MilestonePrerequisiteLink;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneStatus;
import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyMilestoneLinkRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.milestone.MilestoneCompletionStatsRepository;
import com.accsaber.backend.repository.milestone.MilestonePrerequisiteLinkRepository;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

import lombok.RequiredArgsConstructor;

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
                    .completed(true)
                    .completedAt(link.getCompletedAt())
                    .completionPercentage(stats != null ? stats.getCompletionPercentage() : BigDecimal.ZERO)
                    .setId(m.getMilestoneSet().getId())
                    .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
                    .build();
        }).toList();
    }

    public List<MilestoneCompletionResponse> findAllCompletionStats(Long userId) {
        List<Milestone> milestones = milestoneRepository.findByActiveTrueAndStatus(MilestoneStatus.ACTIVE);
        Map<UUID, MilestoneCompletionStats> statsMap = completionStatsRepository.findAll().stream()
                .collect(Collectors.toMap(MilestoneCompletionStats::getMilestoneId, Function.identity()));

        Map<UUID, UserMilestoneLink> userLinkMap = Map.of();
        if (userId != null) {
            Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
            List<UUID> milestoneIds = milestones.stream().map(Milestone::getId).toList();
            userLinkMap = userMilestoneLinkRepository
                    .findCompletedByUserWithScoreDetails(resolved, milestoneIds).stream()
                    .collect(Collectors.toMap(l -> l.getMilestone().getId(), Function.identity()));
        }

        Map<UUID, UserMilestoneLink> finalUserLinkMap = userLinkMap;
        return milestones.stream()
                .map(m -> toCompletionResponse(m, statsMap.get(m.getId()), finalUserLinkMap.get(m.getId())))
                .toList();
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

            return UserMilestoneProgressResponse.builder()
                    .milestoneId(m.getId())
                    .title(m.getTitle())
                    .description(m.getDescription())
                    .type(m.getType())
                    .tier(m.getTier().name())
                    .xp(m.getXp())
                    .targetValue(m.getTargetValue())
                    .progress(link != null ? link.getProgress() : null)
                    .completed(link != null && link.isCompleted())
                    .completedAt(link != null ? link.getCompletedAt() : null)
                    .completionPercentage(stats != null ? stats.getCompletionPercentage() : BigDecimal.ZERO)
                    .setId(m.getMilestoneSet().getId())
                    .categoryId(m.getCategory() != null ? m.getCategory().getId() : null)
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
    public void deactivateMilestone(UUID id) {
        Milestone milestone = milestoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
        milestone.setActive(false);
        milestoneRepository.save(milestone);
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void backfillMilestone(UUID milestoneId) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrueEager(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        List<Long> userIds = userRepository.findByActiveTrue().stream()
                .map(User::getId)
                .toList();
        for (Long userId : userIds) {
            milestoneEvaluationService.evaluateSingleMilestoneForUser(userId, milestone);
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
            builder.userCompleted(true).userCompletedAt(userLink.getCompletedAt());
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
