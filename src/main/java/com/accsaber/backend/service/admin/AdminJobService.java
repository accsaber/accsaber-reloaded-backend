package com.accsaber.backend.service.admin;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.admin.RunJobRequest;
import com.accsaber.backend.model.dto.response.admin.JobTypeResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.item.ItemSerialResequenceService;
import com.accsaber.backend.service.map.ComplexityEstimateService;
import com.accsaber.backend.service.media.CdnSyncService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.milestone.MilestoneService;
import com.accsaber.backend.service.score.ScoreImportService;
import com.accsaber.backend.service.score.ScoreIngestionService;
import com.accsaber.backend.service.score.ScoreRecalculationService;
import com.accsaber.backend.service.score.XPReweightService;
import com.accsaber.backend.service.songsuggest.SongSuggestService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminJobService {

    private final JobRegistry registry;
    private final ScoreRecalculationService scoreRecalculationService;
    private final XPReweightService xpReweightService;
    private final ScoreImportService scoreImportService;
    private final ScoreIngestionService scoreIngestionService;
    private final CdnSyncService cdnSyncService;
    private final MilestoneService milestoneService;
    private final MilestoneEvaluationService milestoneEvaluationService;
    private final SongSuggestService songSuggestService;
    private final ItemSerialResequenceService itemSerialResequenceService;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final ComplexityEstimateService complexityEstimateService;

    public List<JobTypeResponse> catalogue() {
        return Arrays.stream(JobType.values()).map(JobTypeResponse::from).toList();
    }

    public JobRecord run(RunJobRequest request) {
        validate(request);
        JobRecord job = registry.start(request.getType(), describe(request));
        CompletableFuture<Void> work;
        try {
            work = dispatch(request);
        } catch (RuntimeException e) {
            registry.fail(job.id(), e);
            throw e;
        }
        work.whenComplete((ignored, error) -> {
            if (error != null) {
                registry.fail(job.id(), error);
            } else {
                registry.succeed(job.id());
            }
        });
        return job;
    }

    public List<JobRecord> list() {
        return registry.list();
    }

    public Optional<JobRecord> find(UUID jobId) {
        return registry.find(jobId);
    }

    private void validate(RunJobRequest request) {
        for (JobField field : request.getType().getFields()) {
            if (!field.required()) {
                continue;
            }
            Object value = field.reader().apply(request);
            if (value == null || (value instanceof Collection<?> values && values.isEmpty())) {
                throw new ValidationException(field.key(), "is required for " + request.getType());
            }
        }
    }

    private CompletableFuture<Void> dispatch(RunJobRequest request) {
        return switch (request.getType()) {
            case RECALCULATE_AP_DIFFICULTY ->
                scoreRecalculationService.recalculateDifficultyAsync(request.getDifficultyId());
            case RECALCULATE_AP_DIFFICULTIES ->
                scoreRecalculationService.recalculateBatchAsync(loadDifficulties(request));
            case RECALCULATE_AP_RAW -> scoreRecalculationService.recalculateAllRawApAsync();
            case RECALCULATE_AP_WEIGHTED -> scoreRecalculationService.recalculateAllWeightedApAsync();
            case RECALCULATE_AP_ALL -> scoreRecalculationService.recalculateAllApAsync();
            case RECALCULATE_XP_SCORES -> xpReweightService.reweightAllScores();
            case RECALCULATE_XP_TOTALS -> xpReweightService.recalculateTotalXpForAllUsers();
            case RECALCULATE_XP_USER -> xpReweightService.recalculateTotalXpForUser(request.getUserId());

            case BACKFILL_SCORES_ALL -> scoreImportService.backfillAllRankedDifficulties();
            case BACKFILL_SCORES_DIFFICULTY ->
                scoreImportService.backfillDifficultyAsync(request.getDifficultyId());
            case BACKFILL_SCORES_DIFFICULTIES ->
                scoreImportService.backfillDifficultiesAsync(request.getDifficultyIds());
            case BACKFILL_SCORES_USER -> scoreImportService.backfillUserAsync(request.getUserId());
            case BACKFILL_SCORES_USERS -> scoreImportService.backfillUsersAsync(request.getUserIds());
            case BACKFILL_SCORES_GAP_FILL ->
                scoreIngestionService.gapFillSince(request.getSince(), request.getPlatform());
            case BACKFILL_CAMPAIGN_LEGACY ->
                scoreImportService.recheckLegacyCampaign(request.getCampaignId(), request.getUserId());
            case RESETTLE_CAMPAIGN ->
                scoreImportService.resettleCampaign(request.getCampaignId(), request.getUserId());

            case BACKFILL_CDN_MAP_COVERS -> cdnSyncService.backfillAllMapCovers(request.isForce());
            case BACKFILL_CDN_AVATARS -> cdnSyncService.backfillAllUserAvatars(request.isForce());

            case BACKFILL_MILESTONE -> milestoneService.backfillMilestone(request.getMilestoneId());
            case BACKFILL_MILESTONES_ALL -> milestoneService.backfillAllMilestones();
            case BACKFILL_MILESTONES_USER -> milestoneService.backfillUser(request.getUserId());

            case REGRANT_MILESTONE_REWARDS ->
                milestoneEvaluationService.regrantRewards(request.getMilestoneId());
            case REGRANT_MILESTONE_REWARDS_ALL -> milestoneEvaluationService.regrantAllRewards();

            case RESEQUENCE_ITEM_SERIALS -> request.getItemId() != null
                    ? itemSerialResequenceService.resequence(request.getItemId())
                    : itemSerialResequenceService.resequenceAll();

            case REGENERATE_SONG_SUGGEST -> songSuggestService.regenerateAsync();
            case REFRESH_COMPLEXITY_ESTIMATES -> complexityEstimateService.refreshAllAsync();
        };
    }

    private String describe(RunJobRequest request) {
        if (request.getDifficultyId() != null) {
            return "difficulty " + request.getDifficultyId();
        }
        if (request.getCampaignId() != null) {
            return request.getUserId() != null
                    ? "campaign " + request.getCampaignId() + " user " + request.getUserId()
                    : "campaign " + request.getCampaignId();
        }
        if (request.getUserId() != null) {
            return "user " + request.getUserId();
        }
        if (request.getItemId() != null) {
            return "item " + request.getItemId();
        }
        if (request.getDifficultyIds() != null && !request.getDifficultyIds().isEmpty()) {
            return request.getDifficultyIds().size() + " difficulties";
        }
        if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            return request.getUserIds().size() + " users";
        }
        if (request.getSince() != null) {
            return "since " + request.getSince();
        }
        return null;
    }

    private List<MapDifficulty> loadDifficulties(RunJobRequest request) {
        return mapDifficultyRepository.findAllByIdInAndActiveTrueWithCategory(request.getDifficultyIds());
    }
}
