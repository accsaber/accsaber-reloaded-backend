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

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetRequest;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.dto.response.milestone.UserMilestoneProgressResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneCompletionStats;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.milestone.MilestoneCompletionStatsRepository;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;

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
    private final MilestoneEvaluationService milestoneEvaluationService;
    private final MilestoneQueryBuilderService queryBuilderService;

    public Page<MilestoneResponse> findAllActive(UUID setId, UUID categoryId, String type, Pageable pageable) {
        Page<Milestone> milestones = milestoneRepository.findAllActiveFiltered(setId, categoryId, type, pageable);

        Map<UUID, MilestoneCompletionStats> statsMap = completionStatsRepository.findAll().stream()
                .collect(Collectors.toMap(MilestoneCompletionStats::getMilestoneId, Function.identity()));

        return milestones.map(m -> toResponse(m, statsMap.get(m.getId())));
    }

    public MilestoneResponse findById(UUID id) {
        Milestone milestone = milestoneRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
        MilestoneCompletionStats stats = completionStatsRepository.findByMilestoneId(id).orElse(null);
        return toResponse(milestone, stats);
    }

    public Page<MilestoneSetResponse> findAllSets(Pageable pageable) {
        return milestoneSetRepository.findByActiveTrue(pageable)
                .map(this::toSetResponse);
    }

    public Page<UserMilestoneProgressResponse> findUserProgress(Long userId, Pageable pageable) {
        Page<Milestone> allActive = milestoneRepository.findAllActiveFiltered(null, null, null, pageable);
        List<UserMilestoneLink> userLinks = userMilestoneLinkRepository.findByUser_Id(userId);
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
        return toSetResponse(milestoneSetRepository.save(set));
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
                .build();
        return toResponse(milestoneRepository.save(milestone), null);
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
                .completionPercentage(stats != null ? stats.getCompletionPercentage() : BigDecimal.ZERO)
                .completions(stats != null ? stats.getCompletions() : 0L)
                .totalPlayers(stats != null ? stats.getTotalPlayers() : 0L)
                .createdAt(m.getCreatedAt())
                .build();
    }

    private MilestoneSetResponse toSetResponse(MilestoneSet s) {
        return MilestoneSetResponse.builder()
                .id(s.getId())
                .title(s.getTitle())
                .description(s.getDescription())
                .setBonusXp(s.getSetBonusXp())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
