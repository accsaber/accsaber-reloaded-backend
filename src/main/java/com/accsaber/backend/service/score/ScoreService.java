package com.accsaber.backend.service.score;

import com.accsaber.backend.util.Rounding;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.dto.response.score.MyScoreSummary;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.score.ScoresAroundResponse;
import com.accsaber.backend.model.dto.response.score.UserScoreSummaryResponse;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.campaign.CampaignEvaluationService;
import com.accsaber.backend.service.item.LevelUpAwardService;
import com.accsaber.backend.service.item.StrangeTrackingService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserRelationService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;
import com.accsaber.backend.util.HmdMapper;
import com.accsaber.backend.util.MapDifficultyMetrics;
import com.accsaber.backend.util.TimeRangeUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScoreService {

        private static final int ACCURACY_SCALE = 10;
        private static final Duration PLAY_MATCH_WINDOW = Duration.ofSeconds(10);

        private final ScoreRepository scoreRepository;
        private final ScoreModifierLinkRepository modifierLinkRepository;
        private final MapDifficultyRepository mapDifficultyRepository;
        private final ModifierRepository modifierRepository;
        private final UserRepository userRepository;
        private final MapDifficultyComplexityService mapComplexityService;
        private final APCalculationService apCalculationService;
        private final StatisticsService statisticsService;
        private final RankingService rankingService;
        private final XPCalculationService xpCalculationService;
        private final MilestoneEvaluationService milestoneEvaluationService;
        private final MapDifficultyStatisticsService mapDifficultyStatisticsService;
        private final ScoreRankingService scoreRankingService;
        private final DuplicateUserService duplicateUserService;
        private final UserRelationService userRelationService;
        private final com.accsaber.backend.service.skill.SkillService skillService;
        private final LevelUpAwardService levelUpAwardService;
        private final CampaignEvaluationService campaignEvaluationService;
        private final com.accsaber.backend.service.infra.ModifierCacheService modifierCacheService;
        private final ApplicationEventPublisher eventPublisher;
        private final TransactionTemplate transactionTemplate;
        private final com.accsaber.backend.service.supporter.SupporterService supporterService;
        private final StrangeTrackingService strangeTrackingService;

        @Transactional
        public ScoreResponse submit(SubmitScoreRequest request) {
                acquireSubmitLock(request.getUserId(), request.getMapDifficultyId());
                MapDifficulty difficulty = loadRankedDifficulty(request.getMapDifficultyId());
                validateScoreBounds(request, difficulty, true);
                User user = loadActiveUser(request.getUserId());

                Optional<Score> playMatch = findRecentMatchingPlay(user.getId(), difficulty.getId(), request);
                if (playMatch.isPresent()) {
                        return backfillExistingScore(playMatch.get(), request, difficulty);
                }

                Optional<Score> existing = scoreRepository
                                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(user.getId(), difficulty.getId());

                ensurePlayCount(request, user.getId(), difficulty.getId());

                List<Modifier> modifiers = resolveModifiers(request.getModifierIds());
                Integer modifiedScore = applyModifierMultiplier(request.getScore(), modifiers);

                Double accuracy = computeAccuracy(modifiedScore, difficulty.getMaxScore());
                Double complexity = mapComplexityService.findActiveComplexity(difficulty.getId())
                                .orElseThrow(() -> new ValidationException(
                                                "No active complexity set for this map difficulty"));

                APResult apResult = apCalculationService.calculateRawAP(
                                accuracy, complexity, difficulty.getCategory().getScoreCurve());
                Double rawAp = apResult.rawAP();

                Double xpGained;
                boolean isPartial = request.isPartial();
                boolean isWorseThanExisting = existing.isPresent()
                                && request.getScoreNoMods().compareTo(existing.get().getScoreNoMods()) <= 0;
                if (isPartial || isWorseThanExisting) {
                        xpGained = xpCalculationService.calculateXpForWorseScore();
                        Score history = buildScore(request, user, difficulty, modifiedScore, rawAp, null);
                        history.setActive(false);
                        history.setSupersedesReason(isPartial ? "Partial attempt" : "Worse score");
                        history.setXpGained(xpGained);
                        scoreRepository.saveAndFlush(history);
                        saveModifierLinks(history, modifiers);
                        updateUserXp(user.getId(), xpGained);

                        milestoneEvaluationService.evaluateAfterScore(user.getId(), history);
                        campaignEvaluationService.evaluateAfterScore(user.getId(), history);

                        ScoreResponse worseResponse = toResponse(history,
                                        computeAccuracy(history.getScore(), difficulty.getMaxScore()),
                                        loadModifierIds(history.getId()));
                        eventPublisher.publishEvent(
                                        new ScoreSubmittedEvent(withMapMetadata(worseResponse, difficulty)));
                        return worseResponse;
                }

                Score supersedes = existing.orElse(null);
                int newRank;
                if (supersedes != null) {
                        Double oldAccuracy = computeAccuracy(supersedes.getScore(), difficulty.getMaxScore());
                        xpGained = xpCalculationService.calculateXpForImprovement(
                                        accuracy, oldAccuracy, complexity);
                        int oldRank = supersedes.getRank();
                        supersedes.setActive(false);
                        supersedes.setSupersedesReason("Score improved");
                        scoreRepository.saveAndFlush(supersedes);
                        newRank = scoreRankingService.rankImprovedScore(difficulty.getId(), oldRank, rawAp,
                                        request.getTimeSet());
                } else {
                        xpGained = xpCalculationService.calculateXpForNewMap(accuracy, complexity);
                        newRank = scoreRankingService.rankNewScore(difficulty.getId(), rawAp, request.getTimeSet());
                }

                Score newScore = buildScore(request, user, difficulty, modifiedScore, rawAp, supersedes);
                newScore.setRank(newRank);
                newScore.setRankWhenSet(newRank);
                newScore.setXpGained(xpGained);
                Score saved = scoreRepository.saveAndFlush(newScore);
                saveModifierLinks(saved, modifiers);

                updateUserXp(user.getId(), xpGained);
                strangeTrackingService.recordPlay(user.getId());

                statisticsService.recalculate(user.getId(), difficulty.getCategory().getId());
                mapDifficultyStatisticsService.recalculate(difficulty, user.getId());

                final Long userId = user.getId();
                final UUID scoreId = saved.getId();
                final ScoreResponse response = toResponse(saved, accuracy, loadModifierIds(saved.getId()));

                rankingService.updateRankingForUserAsync(difficulty.getCategory().getId(), userId, () -> {
                        transactionTemplate.executeWithoutResult(status -> {
                                Score freshScore = scoreRepository.findById(scoreId).orElse(null);
                                if (freshScore != null) {
                                        milestoneEvaluationService.evaluateAfterScore(userId, freshScore);
                                        campaignEvaluationService.evaluateAfterScore(userId, freshScore);
                                }
                                eventPublisher.publishEvent(
                                                new ScoreSubmittedEvent(withMapMetadata(response, difficulty)));
                        });
                });

                return response;
        }

        @Transactional
        public Optional<ScoreResponse> submitPlayer(SubmitScoreRequest request) {
                MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(request.getMapDifficultyId())
                                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty",
                                                request.getMapDifficultyId()));
                if (difficulty.getStatus() == MapDifficultyStatus.RANKED && !carriesBannedModifier(request)) {
                        return Optional.of(submit(request));
                }
                return submitCampaignScore(request);
        }

        @Transactional
        public Optional<ScoreResponse> submitCampaignScore(SubmitScoreRequest request) {
                if (request.isPartial()) {
                        return Optional.empty();
                }
                acquireSubmitLock(request.getUserId(), request.getMapDifficultyId());
                MapDifficulty difficulty = loadCampaignDifficulty(request.getMapDifficultyId(),
                                carriesBannedModifier(request));
                validateScoreBounds(request, difficulty, true);
                if (!campaignEvaluationService.isRecordable(request.getUserId(), difficulty.getId())) {
                        throw new ValidationException(
                                        "No campaign you are playing has this map difficulty unlocked");
                }
                User user = loadUserForBackfill(request.getUserId());

                Optional<Score> attemptMatch = findRecentCampaignAttempt(user.getId(), difficulty.getId(), request);
                if (attemptMatch.isPresent()) {
                        Score existing = attemptMatch.get();
                        if (ScorePayloadFields.merge(existing, request)) {
                                scoreRepository.saveAndFlush(existing);
                        }
                        campaignEvaluationService.evaluateAfterScore(user.getId(), existing);
                        return Optional.of(toResponse(existing,
                                        computeAccuracy(existing.getScore(), difficulty.getMaxScore()),
                                        loadModifierIds(existing.getId())));
                }

                List<Modifier> modifiers = resolveModifiers(request.getModifierIds());
                Integer modifiedScore = applyModifierMultiplier(request.getScore(), modifiers);

                Score attempt = buildScore(request, user, difficulty, modifiedScore, 0.0, null);
                attempt.setRank(0);
                attempt.setRankWhenSet(0);
                attempt.setActive(false);
                attempt.setSupersedesReason("Campaign attempt");
                attempt.setXpGained(0.0);
                Score saved = scoreRepository.saveAndFlush(attempt);
                saveModifierLinks(saved, modifiers);

                campaignEvaluationService.evaluateAfterScore(user.getId(), saved);

                return Optional.of(toResponse(saved,
                                computeAccuracy(saved.getScore(), difficulty.getMaxScore()),
                                loadModifierIds(saved.getId())));
        }

        @Transactional
        public void recordCampaignBackfillScore(SubmitScoreRequest request) {
                if (request.isPartial()) {
                        return;
                }
                MapDifficulty difficulty = loadCampaignDifficulty(request.getMapDifficultyId(),
                                carriesBannedModifier(request));
                validateScoreBounds(request, difficulty, true);
                User user = loadUserForBackfill(request.getUserId());

                List<Modifier> modifiers = resolveModifiers(request.getModifierIds());
                Integer modifiedScore = applyModifierMultiplier(request.getScore(), modifiers);

                Score attempt = buildScore(request, user, difficulty, modifiedScore, 0.0, null);
                attempt.setRank(0);
                attempt.setRankWhenSet(0);
                attempt.setActive(false);
                attempt.setSupersedesReason("Campaign attempt");
                attempt.setXpGained(0.0);
                Score saved = scoreRepository.saveAndFlush(attempt);
                saveModifierLinks(saved, modifiers);
        }

        private boolean carriesBannedModifier(SubmitScoreRequest request) {
                return modifierCacheService.containsBannedModifier(request.getModifierIds());
        }

        private MapDifficulty loadCampaignDifficulty(UUID id, boolean allowRanked) {
                MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(id)
                                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", id));
                if (difficulty.getStatus() == MapDifficultyStatus.RANKED && !allowRanked) {
                        throw new ValidationException("Ranked difficulties use the standard submission path");
                }
                if (difficulty.getMaxScore() == null || difficulty.getMaxScore() <= 0) {
                        throw new ValidationException("Map difficulty has no valid max score configured");
                }
                return difficulty;
        }

        @Transactional
        public void submitForBackfill(SubmitScoreRequest request) {
                MapDifficulty difficulty = loadRankedDifficulty(request.getMapDifficultyId());
                Double complexity = mapComplexityService.findActiveComplexity(difficulty.getId())
                                .orElseThrow(() -> new ValidationException(
                                                "No active complexity set for this map difficulty"));
                doSubmitForBackfill(request, difficulty, complexity);
        }

        @Transactional
        public void submitForBackfill(SubmitScoreRequest request, MapDifficulty difficulty, Double complexity) {
                doSubmitForBackfill(request, difficulty, complexity);
        }

        private void doSubmitForBackfill(SubmitScoreRequest request, MapDifficulty difficulty, Double complexity) {
                acquireSubmitLock(request.getUserId(), difficulty.getId());
                validateScoreBounds(request, difficulty, false);
                User user = loadUserForBackfill(request.getUserId());

                Optional<Score> playMatch = findRecentMatchingPlay(user.getId(), difficulty.getId(), request);
                if (playMatch.isPresent()) {
                        backfillExistingScore(playMatch.get(), request, difficulty);
                        return;
                }

                Optional<Score> existing = scoreRepository
                                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(user.getId(), difficulty.getId());

                ensurePlayCount(request, user.getId(), difficulty.getId());

                List<Modifier> modifiers = resolveModifiers(request.getModifierIds());
                Integer modifiedScore = applyModifierMultiplier(request.getScore(), modifiers);

                Double accuracy = computeAccuracy(modifiedScore, difficulty.getMaxScore());
                APResult apResult = apCalculationService.calculateRawAP(
                                accuracy, complexity, difficulty.getCategory().getScoreCurve());
                Double rawAp = apResult.rawAP();

                Double xpGained;
                boolean isPartial = request.isPartial();
                boolean isWorseThanExisting = existing.isPresent()
                                && request.getScoreNoMods().compareTo(existing.get().getScoreNoMods()) <= 0;
                if (isPartial || isWorseThanExisting) {
                        xpGained = xpCalculationService.calculateXpForWorseScore();
                        Score history = buildScore(request, user, difficulty, modifiedScore, rawAp, null);
                        history.setActive(false);
                        history.setSupersedesReason(isPartial ? "Partial attempt" : "Worse score");
                        history.setXpGained(xpGained);
                        scoreRepository.saveAndFlush(history);
                        saveModifierLinks(history, modifiers);
                        updateUserXp(user.getId(), xpGained);
                        return;
                }

                Score supersedes = existing.orElse(null);
                if (supersedes != null) {
                        Double oldAccuracy = computeAccuracy(supersedes.getScore(), difficulty.getMaxScore());
                        xpGained = xpCalculationService.calculateXpForImprovement(
                                        accuracy, oldAccuracy, complexity);
                        supersedes.setActive(false);
                        scoreRepository.saveAndFlush(supersedes);
                } else {
                        xpGained = xpCalculationService.calculateXpForNewMap(accuracy, complexity);
                }

                Score newScore = buildScore(request, user, difficulty, modifiedScore, rawAp, supersedes);
                newScore.setXpGained(xpGained);
                Score saved = scoreRepository.saveAndFlush(newScore);
                saveModifierLinks(saved, modifiers);

                updateUserXp(user.getId(), xpGained);
        }

        record RecalcResult(Long userId, UUID categoryId, UUID difficultyId) {
        }

        @Transactional
        public void recalculateScore(UUID scoreId) {
                RecalcResult result = recalculateScoreForBatch(scoreId);
                if (result == null)
                        return;
                scoreRankingService.reassignRanks(result.difficultyId());
                statisticsService.recalculate(result.userId(), result.categoryId());
                rankingService.updateRankingsAsync(result.categoryId(),
                                () -> skillService.upsertSkill(result.userId(), result.categoryId()));
        }

        @Transactional
        public RecalcResult recalculateScoreForBatch(UUID scoreId) {
                Score score = scoreRepository.findByIdWithUser(scoreId).orElse(null);
                if (score == null || !score.isActive())
                        return null;
                MapDifficulty difficulty = score.getMapDifficulty();
                Double complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
                if (complexity == null)
                        return null;
                return doRecalculateScoreForBatch(score, difficulty, complexity);
        }

        @Transactional
        public RecalcResult recalculateScoreForBatch(UUID scoreId, MapDifficulty difficulty, Double complexity) {
                Score score = scoreRepository.findByIdWithUser(scoreId).orElse(null);
                if (score == null || !score.isActive())
                        return null;
                if (!score.getMapDifficulty().getId().equals(difficulty.getId())) {
                        throw new IllegalArgumentException(
                                        "Provided difficulty does not match score's map difficulty");
                }
                return doRecalculateScoreForBatch(score, difficulty, complexity);
        }

        private RecalcResult doRecalculateScoreForBatch(Score score, MapDifficulty difficulty, Double complexity) {
                Double accuracy = computeAccuracy(score.getScore(), difficulty.getMaxScore());
                APResult apResult = apCalculationService.calculateRawAP(
                                accuracy, complexity, difficulty.getCategory().getScoreCurve());

                if ((apResult.rawAP() == score.getAp()))
                        return null;

                Double oldXp = score.getXpGained() != null ? score.getXpGained() : 0.0;
                score.setActive(false);
                score.setXpGained(0.0);
                scoreRepository.saveAndFlush(score);

                Score recalculated = Score.builder()
                                .user(score.getUser())
                                .mapDifficulty(difficulty)
                                .score(score.getScore())
                                .scoreNoMods(score.getScoreNoMods())
                                .rank(score.getRank())
                                .rankWhenSet(score.getRankWhenSet())
                                .ap(apResult.rawAP())
                                .weightedAp(0.0)
                                .reweightDerivative(true)
                                .xpGained(0.0)
                                .supersedes(score)
                                .supersedesReason("Complexity reweight")
                                .active(true)
                                .build();
                ScorePayloadFields.copyAll(score, recalculated);
                if (recalculated.getTimeSet() == null) {
                        recalculated.setTimeSet(score.getCreatedAt());
                }

                scoreRepository.saveAndFlush(recalculated);
                copyModifierLinks(score, recalculated);
                if (oldXp.compareTo(0.0) > 0) {
                        levelUpAwardService.addXp(score.getUser().getId(), (-oldXp));
                }

                return new RecalcResult(score.getUser().getId(), difficulty.getCategory().getId(), difficulty.getId());
        }

        public ScoreResponse findActiveByUserAndSongHash(Long userId, String songHash, Difficulty difficulty,
                        String characteristic) {
                Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
                Score score = scoreRepository
                                .findActiveByUserAndSongHashAndDifficultyAndCharacteristic(
                                                resolvedUserId, songHash, difficulty, characteristic)
                                .orElseThrow(() -> new ResourceNotFoundException("Score",
                                                resolvedUserId + "/" + songHash + "/" + difficulty + "/"
                                                                + characteristic));
                return mapToResponse(score);
        }

        public Page<ScoreResponse> findByUser(Long userId, UUID categoryId, String search, Pageable pageable) {
                Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
                Page<Score> scores = queryActiveByUser(resolvedUserId, categoryId, search,
                                resolveSort(pageable, Sort.by(Sort.Direction.DESC, "ap")));

                java.util.Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                                scores.getContent().stream().map(Score::getId).toList());
                java.util.Map<UUID, Double> complexities = mapComplexityService
                                .findActiveComplexitiesForDifficulties(scores.getContent().stream()
                                                .map(s -> s.getMapDifficulty().getId()).distinct().toList());
                java.util.Map<AttemptKey, AttemptAggregate> aggregates = loadAttemptAggregatesBatch(
                                scores.getContent());
                return scores.map(s -> toResponse(s, computeAccuracy(s.getScore(), s.getMapDifficulty().getMaxScore()),
                                modifierIds.getOrDefault(s.getId(), List.of()))
                                .toBuilder()
                                .complexity(complexities.get(s.getMapDifficulty().getId()))
                                .maxStreak115(maxStreakFor(aggregates, s))
                                .playCount(playCountFor(aggregates, s))
                                .lastPlayedAt(lastPlayedAtFor(aggregates, s))
                                .build());
        }

        public List<MapDifficulty> findDifficultiesByUser(Long userId, UUID categoryId, String search,
                        Pageable pageable) {
                Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
                List<UUID> orderedIds = queryActiveByUser(resolvedUserId, categoryId, search,
                                resolveSort(pageable, Sort.by(Sort.Direction.DESC, "ap")))
                                .getContent().stream()
                                .map(s -> s.getMapDifficulty().getId())
                                .distinct()
                                .toList();
                if (orderedIds.isEmpty()) {
                        return List.of();
                }

                java.util.Map<UUID, MapDifficulty> byId = mapDifficultyRepository
                                .findAllByIdInAndActiveTrueWithMapAndCategory(orderedIds).stream()
                                .collect(java.util.stream.Collectors.toMap(MapDifficulty::getId, d -> d));
                return orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        }

        private Page<Score> queryActiveByUser(Long resolvedUserId, UUID categoryId, String search, Pageable pageable) {
                boolean hasSearch = search != null && !search.isBlank();
                if (categoryId != null && hasSearch) {
                        return scoreRepository.findActiveByUserAndCategoryAndSongSearch(
                                        resolvedUserId, categoryId, search.trim(), pageable);
                }
                if (categoryId != null) {
                        return scoreRepository.findActiveByUserAndCategory(resolvedUserId, categoryId, pageable);
                }
                if (hasSearch) {
                        return scoreRepository.findActiveByUserAndSongSearch(
                                        resolvedUserId, search.trim(), pageable);
                }
                return scoreRepository.findActiveByUser(resolvedUserId, pageable);
        }

        public List<UserScoreSummaryResponse> findAllSummariesByUser(Long userId) {
                Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
                return scoreRepository.findActiveScoreSummariesByUser(resolvedUserId).stream()
                                .map(row -> {
                                        Integer score = (Integer) row[5];
                                        Integer maxScore = (Integer) row[6];
                                        return UserScoreSummaryResponse.builder()
                                                        .mapDifficultyId((UUID) row[0])
                                                        .songHash((String) row[1])
                                                        .songName((String) row[11])
                                                        .songSubName((String) row[12])
                                                        .songAuthor((String) row[13])
                                                        .coverUrl((String) row[14])
                                                        .cdnCoverUrl((String) row[15])
                                                        .ssLeaderboardId((String) row[2])
                                                        .blLeaderboardId((String) row[3])
                                                        .ap((Double) row[4])
                                                        .accuracy(computeAccuracy(score, maxScore))
                                                        .score(score)
                                                        .maxScore(maxScore)
                                                        .rank((Integer) row[7])
                                                        .blScoreId((Long) row[8])
                                                        .ssScoreId((Long) row[9])
                                                        .timeSet((Instant) row[10])
                                                        .build();
                                })
                                .toList();
        }

        public Page<ScoreResponse> findByUserRelations(Long viewerUserId, UserRelationType type, UUID categoryId,
                        String search, Pageable pageable) {
                return findByUserRelations(viewerUserId, type, categoryId, search, false, pageable);
        }

        public Page<ScoreResponse> findByUserRelations(Long viewerUserId, UserRelationType type, UUID categoryId,
                        String search, boolean includePrincipal, Pageable pageable) {
                Pageable effective = resolveSort(pageable, Sort.by(Sort.Direction.DESC, "ap"));
                List<UserRelationType> types = type != null
                                ? List.of(type)
                                : List.of(UserRelationType.follower, UserRelationType.rival);
                if (types.contains(UserRelationType.blocked)) {
                        throw new ValidationException("Cannot list scores of blocked users");
                }
                List<Long> userIds = userRelationService.findActiveTargetUserIdsByTypes(viewerUserId, types);
                if (includePrincipal) {
                        HashSet<Long> includedUserIds = new HashSet<>(userIds);
                        includedUserIds.add(viewerUserId);
                        userIds = List.copyOf(includedUserIds);
                }
                if (userIds.isEmpty()) {
                        return Page.empty(effective);
                }
                boolean hasSearch = search != null && !search.isBlank();
                Page<Score> scores;
                if (categoryId != null && hasSearch) {
                        scores = scoreRepository.findActiveByUsersAndCategoryAndSongSearch(
                                        userIds, categoryId, search.trim(), effective);
                } else if (categoryId != null) {
                        scores = scoreRepository.findActiveByUsersAndCategory(userIds, categoryId, effective);
                } else if (hasSearch) {
                        scores = scoreRepository.findActiveByUsersAndSongSearch(
                                        userIds, search.trim(), effective);
                } else {
                        scores = scoreRepository.findActiveByUsers(userIds, effective);
                }

                List<UUID> difficultyIds = scores.getContent().stream()
                                .map(s -> s.getMapDifficulty().getId())
                                .distinct()
                                .toList();
                java.util.Map<UUID, Score> myByDifficulty = difficultyIds.isEmpty()
                                ? java.util.Map.of()
                                : scoreRepository.findActiveByUserAndMapDifficultyIdIn(viewerUserId, difficultyIds)
                                                .stream()
                                                .collect(java.util.stream.Collectors.toMap(
                                                                vs -> vs.getMapDifficulty().getId(),
                                                                java.util.function.Function.identity(),
                                                                (a, b) -> a));

                java.util.Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                                scores.getContent().stream().map(Score::getId).toList());
                return scores.map(s -> {
                        Integer maxScore = s.getMapDifficulty().getMaxScore();
                        ScoreResponse base = toResponse(s, computeAccuracy(s.getScore(), maxScore),
                                        modifierIds.getOrDefault(s.getId(), List.of()));
                        Score mine = myByDifficulty.get(s.getMapDifficulty().getId());
                        if (mine == null) {
                                return base;
                        }
                        return base.toBuilder()
                                        .myScore(MyScoreSummary.builder()
                                                        .id(mine.getId())
                                                        .score(mine.getScore())
                                                        .accuracy(computeAccuracy(mine.getScore(), maxScore))
                                                        .ap(mine.getAp())
                                                        .weightedAp(mine.getWeightedAp())
                                                        .rank(mine.getRank())
                                                        .timeSet(mine.getTimeSet())
                                                        .build())
                                        .build();
                });
        }

        public Page<ScoreResponse> findByMapDifficulty(UUID mapDifficultyId, Pageable pageable) {
                MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
                if (difficulty.getMaxScore() == null || difficulty.getMaxScore() <= 0) {
                        throw new ValidationException("Map difficulty has no valid max score configured");
                }
                Pageable effective = resolveSort(pageable, Sort.by(Sort.Direction.ASC, "rank"));
                Page<Score> scores = scoreRepository.findByMapDifficulty_IdAndActiveTrue(mapDifficultyId, effective);
                java.util.Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                                scores.getContent().stream().map(Score::getId).toList());
                return scores.map(s -> toResponse(s, computeAccuracy(s.getScore(), difficulty.getMaxScore()),
                                modifierIds.getOrDefault(s.getId(), List.of())));
        }

        public Page<ScoreResponse> findLeaderboardByMapDifficulty(UUID mapDifficultyId, String country,
                        String search, Pageable pageable) {
                return findLeaderboardByMapDifficulty(mapDifficultyId, country, search, null, pageable);
        }

        public Page<ScoreResponse> findLeaderboardByMapDifficulty(UUID mapDifficultyId, String country,
                        String search, java.util.Collection<Long> userIdFilter, Pageable pageable) {
                MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
                if (difficulty.getMaxScore() == null || difficulty.getMaxScore() <= 0) {
                        throw new ValidationException("Map difficulty has no valid max score configured");
                }
                Pageable effective = resolveSort(pageable, Sort.by(Sort.Direction.ASC, "rank"));
                if (userIdFilter != null && userIdFilter.isEmpty()) {
                        return Page.empty(effective);
                }
                boolean hasCountry = country != null && !country.isBlank();
                boolean hasSearch = search != null && !search.isBlank();
                Page<Score> scores;
                if (userIdFilter != null) {
                        scores = scoreRepository.findByMapDifficultyIdAndActiveTrueWithUserFilteredByUserIds(
                                        mapDifficultyId, userIdFilter,
                                        hasCountry ? country.toUpperCase() : null,
                                        hasSearch ? search.trim() : null, effective);
                } else if (hasCountry && hasSearch) {
                        scores = scoreRepository.findByMapDifficultyIdAndActiveTrueWithUserAndCountryAndSearch(
                                        mapDifficultyId, country.toUpperCase(), search.trim(), effective);
                } else if (hasCountry) {
                        scores = scoreRepository.findByMapDifficultyIdAndActiveTrueWithUserAndCountry(
                                        mapDifficultyId, country.toUpperCase(), effective);
                } else if (hasSearch) {
                        scores = scoreRepository.findByMapDifficultyIdAndActiveTrueWithUserAndSearch(
                                        mapDifficultyId, search.trim(), effective);
                } else {
                        scores = scoreRepository.findByMapDifficultyIdAndActiveTrueWithUser(
                                        mapDifficultyId, effective);
                }
                java.util.List<Long> userIds = scores.getContent().stream()
                                .map(s -> s.getUser().getId())
                                .toList();
                java.util.Map<Long, String> tiers = supporterService.findCurrentTiersByUserIds(userIds);
                java.util.Map<Long, Double> skillLevels = skillService.findSkillLevelsByUserIds(
                                difficulty.getCategory() != null ? difficulty.getCategory().getId() : null, userIds);
                java.util.Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                                scores.getContent().stream().map(Score::getId).toList());
                java.util.Map<AttemptKey, AttemptAggregate> aggregates = loadAttemptAggregatesBatch(
                                scores.getContent());
                return scores.map(s -> toResponse(s,
                                computeAccuracy(s.getScore(), difficulty.getMaxScore()),
                                modifierIds.getOrDefault(s.getId(), List.of()))
                                .toBuilder()
                                .supporterTier(tiers.get(s.getUser().getId()))
                                .skillLevel(skillLevels.get(s.getUser().getId()))
                                .maxStreak115(maxStreakFor(aggregates, s))
                                .playCount(playCountFor(aggregates, s))
                                .lastPlayedAt(lastPlayedAtFor(aggregates, s))
                                .build());
        }

        public ScoresAroundResponse findScoresAround(UUID mapDifficultyId, Long userId, int above, int below) {
                Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
                MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
                Score playerScore = scoreRepository
                                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(resolvedUserId, mapDifficultyId)
                                .orElseThrow(() -> new ResourceNotFoundException("Score for user", resolvedUserId));

                int rank = playerScore.getRank();
                int total = above + below + 1;
                int offset = Math.max(0, rank - above - 1);
                int fetchSize = offset + total;

                List<Score> scores = scoreRepository.findByMapDifficultyIdAndActiveTrueWithUser(
                                mapDifficultyId, PageRequest.of(0, fetchSize, Sort.by(Sort.Direction.DESC, "score")))
                                .getContent();

                if (offset > 0 && scores.size() > offset) {
                        scores = scores.subList(offset, Math.min(scores.size(), offset + total));
                } else if (offset == 0) {
                        scores = scores.subList(0, Math.min(scores.size(), total));
                }

                int playerIndex = -1;
                for (int i = 0; i < scores.size(); i++) {
                        if (scores.get(i).getUser().getId().equals(resolvedUserId)) {
                                playerIndex = i;
                                break;
                        }
                }

                if (playerIndex == -1) {
                        throw new ResourceNotFoundException("Score for user", resolvedUserId);
                }

                Integer maxScore = difficulty.getMaxScore();
                java.util.Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                                scores.stream().map(Score::getId).toList());
                java.util.Map<AttemptKey, AttemptAggregate> aggregates = loadAttemptAggregatesBatch(scores);
                List<ScoreResponse> aboveScores = scores.subList(0, playerIndex).stream()
                                .map(s -> toResponse(s, computeAccuracy(s.getScore(), maxScore),
                                                modifierIds.getOrDefault(s.getId(), List.of()))
                                                .toBuilder()
                                                .playCount(playCountFor(aggregates, s))
                                                .lastPlayedAt(lastPlayedAtFor(aggregates, s))
                                                .build())
                                .toList();
                ScoreResponse player = toResponse(scores.get(playerIndex),
                                computeAccuracy(scores.get(playerIndex).getScore(), maxScore),
                                modifierIds.getOrDefault(scores.get(playerIndex).getId(), List.of()))
                                .toBuilder()
                                .playCount(playCountFor(aggregates, scores.get(playerIndex)))
                                .lastPlayedAt(lastPlayedAtFor(aggregates, scores.get(playerIndex)))
                                .build();
                List<ScoreResponse> belowScores = scores.subList(playerIndex + 1, scores.size()).stream()
                                .map(s -> toResponse(s, computeAccuracy(s.getScore(), maxScore),
                                                modifierIds.getOrDefault(s.getId(), List.of()))
                                                .toBuilder()
                                                .playCount(playCountFor(aggregates, s))
                                                .lastPlayedAt(lastPlayedAtFor(aggregates, s))
                                                .build())
                                .toList();

                return ScoresAroundResponse.builder()
                                .scoresAbove(aboveScores)
                                .playerScore(player)
                                .scoresBelow(belowScores)
                                .build();
        }

        public List<ScoreResponse> findHistoric(Long userId, UUID mapDifficultyId, int amount, String unit) {
                Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
                Instant since = TimeRangeUtil.computeSince(amount, unit);
                List<Score> scores = scoreRepository.findHistoric(resolvedUserId, mapDifficultyId, since);

                java.util.Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                                scores.stream().map(Score::getId).toList());
                return scores.stream()
                                .map(s -> toResponse(s,
                                                computeAccuracy(s.getScore(),
                                                                s.getMapDifficulty().getMaxScore()),
                                                modifierIds.getOrDefault(s.getId(), List.of())))
                                .toList();
        }

        private void updateUserXp(Long userId, Double xpGained) {
                levelUpAwardService.addXp(userId, xpGained);
        }


        private ScoreResponse withMapMetadata(ScoreResponse response, MapDifficulty difficulty) {
                return response.toBuilder()
                                .metadata(difficulty.getMetadata())
                                .nps(MapDifficultyMetrics.nps(difficulty.getMetadata()))
                                .build();
        }

        private void validateScoreBounds(SubmitScoreRequest request, MapDifficulty difficulty,
                        boolean enforceScoreCeiling) {
                if (request.getScoreNoMods() != null && request.getScoreNoMods() <= 1) {
                        throw new ValidationException("scoreNoMods must be positive");
                }
                if (request.getScore() != null && request.getScore() <= 1) {
                        throw new ValidationException("score must be positive");
                }
                Integer maxCombo = MapDifficultyMetrics.maxCombo(difficulty.getMetadata());
                if (maxCombo != null && request.getMaxCombo() != null && request.getMaxCombo() > maxCombo) {
                        throw new ValidationException("maxCombo exceeds the map's note count");
                }
                Integer max = difficulty.getMaxScore();
                if (max == null || max <= 0) {
                        return;
                }
                if (request.getScoreNoMods() != null && request.getScoreNoMods() > max) {
                        throw new ValidationException("scoreNoMods exceeds the map's maxScore");
                }
                if (enforceScoreCeiling && request.getScore() != null && request.getScore() > max) {
                        throw new ValidationException("score exceeds the map's maxScore");
                }
        }

        private void ensurePlayCount(SubmitScoreRequest request, Long userId, UUID mapDifficultyId) {
                if (request.getPlayCount() != null) {
                        return;
                }
                long prior = scoreRepository.countAttemptsByUserAndDifficulty(userId, mapDifficultyId);
                request.setPlayCount((int) (prior + 1));
        }

        private void acquireSubmitLock(Long userId, UUID mapDifficultyId) {
                scoreRepository.acquireSubmitLock(userId + ":" + mapDifficultyId);
        }

        private Optional<Score> findRecentMatchingPlay(Long userId, UUID mapDifficultyId,
                        SubmitScoreRequest request) {
                if (request.getScoreNoMods() == null) {
                        return Optional.empty();
                }
                Instant playAt = request.isPartial() || request.getTimeSet() == null
                                ? Instant.EPOCH
                                : request.getTimeSet();
                List<Score> matches = scoreRepository.findRecentMatchingPlay(userId, mapDifficultyId,
                                request.getScoreNoMods(), request.isPartial(),
                                Instant.now().minus(Duration.ofDays(1)),
                                playAt.minus(PLAY_MATCH_WINDOW), playAt.plus(PLAY_MATCH_WINDOW),
                                PageRequest.of(0, 1));
                return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
        }

        private Optional<Score> findRecentCampaignAttempt(Long userId, UUID mapDifficultyId,
                        SubmitScoreRequest request) {
                if (request.getScoreNoMods() == null) {
                        return Optional.empty();
                }
                Instant playAt = request.getTimeSet() == null ? Instant.EPOCH : request.getTimeSet();
                List<Score> matches = scoreRepository.findRecentCampaignAttempt(userId, mapDifficultyId,
                                request.getScoreNoMods(), Instant.now().minus(Duration.ofDays(1)),
                                playAt.minus(PLAY_MATCH_WINDOW), playAt.plus(PLAY_MATCH_WINDOW),
                                PageRequest.of(0, 1));
                return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
        }

        private ScoreResponse backfillExistingScore(Score existing, SubmitScoreRequest request,
                        MapDifficulty difficulty) {
                if (ScorePayloadFields.merge(existing, request)) {
                        scoreRepository.saveAndFlush(existing);
                        milestoneEvaluationService.evaluateAfterScore(existing.getUser().getId(), existing);
                }
                return toResponse(existing,
                                computeAccuracy(existing.getScore(), difficulty.getMaxScore()),
                                loadModifierIds(existing.getId()));
        }

        private MapDifficulty loadRankedDifficulty(UUID id) {
                MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(id)
                                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", id));
                if (difficulty.getStatus() != MapDifficultyStatus.RANKED) {
                        throw new ValidationException("Scores can only be submitted for ranked map difficulties");
                }
                if (difficulty.getMaxScore() == null || difficulty.getMaxScore() <= 0) {
                        throw new ValidationException("Map difficulty has no valid max score configured");
                }
                return difficulty;
        }

        private User loadActiveUser(Long userId) {
                User user = loadUserForBackfill(userId);
                if (user.isPlayerInactive()) {
                        user.setPlayerInactive(false);
                        userRepository.save(user);
                }
                return user;
        }

        private User loadUserForBackfill(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
                if (user.isBanned()) {
                        throw new ValidationException("Banned users cannot submit scores");
                }
                return user;
        }

        private Double computeAccuracy(Integer score, Integer maxScore) {
                return Rounding.round((double) (score) / (double) (maxScore), ACCURACY_SCALE);
        }

        private Score buildScore(SubmitScoreRequest req, User user, MapDifficulty difficulty,
                        Integer modifiedScore, Double ap, Score supersedes) {
                Score score = Score.builder()
                                .user(user)
                                .mapDifficulty(difficulty)
                                .score(modifiedScore)
                                .scoreNoMods(req.getScoreNoMods())
                                .rank(req.getRank())
                                .rankWhenSet(req.getRankWhenSet())
                                .ap(ap)
                                .weightedAp(0.0)
                                .supersedes(supersedes)
                                .supersedesReason(supersedes != null ? "Score improved" : null)
                                .active(true)
                                .partial(req.isPartial())
                                .build();
                ScorePayloadFields.applyAll(score, req);
                return score;
        }

        private List<Modifier> resolveModifiers(List<UUID> modifierIds) {
                if (modifierIds == null || modifierIds.isEmpty())
                        return List.of();
                List<Modifier> modifiers = modifierRepository.findAllById(modifierIds);
                if (modifiers.size() != modifierIds.size()) {
                        throw new ValidationException("One or more modifier IDs are invalid");
                }
                return modifiers;
        }

        private Integer applyModifierMultiplier(Integer baseScore, List<Modifier> modifiers) {
                if (modifiers.isEmpty())
                        return baseScore;
                double combined = modifiers.stream()
                                .mapToDouble(Modifier::getMultiplier)
                                .reduce(1.0, (a, b) -> a * b);
                return (int) Rounding.round(combined * baseScore, 0);
        }

        private void saveModifierLinks(Score score, List<Modifier> modifiers) {
                if (modifiers.isEmpty())
                        return;
                List<ScoreModifierLink> links = modifiers.stream()
                                .map(m -> ScoreModifierLink.builder().score(score).modifier(m).build())
                                .toList();
                modifierLinkRepository.saveAll(links);
        }

        private void copyModifierLinks(Score from, Score to) {
                List<ScoreModifierLink> original = modifierLinkRepository.findByScore_Id(from.getId());
                if (original.isEmpty())
                        return;
                List<ScoreModifierLink> copies = original.stream()
                                .map(l -> ScoreModifierLink.builder().score(to).modifier(l.getModifier()).build())
                                .toList();
                modifierLinkRepository.saveAll(copies);
        }

        private List<UUID> loadModifierIds(UUID scoreId) {
                return modifierLinkRepository.findByScore_Id(scoreId).stream()
                                .map(l -> l.getModifier().getId())
                                .toList();
        }

        private java.util.Map<UUID, List<UUID>> loadModifierIdsBatch(java.util.Collection<UUID> scoreIds) {
                if (scoreIds.isEmpty()) {
                        return java.util.Map.of();
                }
                return modifierLinkRepository.findByScore_IdIn(scoreIds).stream()
                                .collect(java.util.stream.Collectors.groupingBy(
                                                l -> l.getScore().getId(),
                                                java.util.stream.Collectors.mapping(
                                                                l -> l.getModifier().getId(),
                                                                java.util.stream.Collectors.toList())));
        }

        private record AttemptKey(Long userId, UUID mapDifficultyId) {
        }

        private record AttemptAggregate(Integer maxStreak115, Integer playCount, Instant lastPlayedAt) {
        }

        private java.util.Map<AttemptKey, AttemptAggregate> loadAttemptAggregatesBatch(
                        java.util.Collection<Score> scores) {
                if (scores.isEmpty()) {
                        return java.util.Map.of();
                }
                List<Long> userIds = scores.stream().map(s -> s.getUser().getId()).distinct().toList();
                List<UUID> difficultyIds = scores.stream().map(s -> s.getMapDifficulty().getId()).distinct().toList();
                java.util.Map<AttemptKey, AttemptAggregate> byKey = new java.util.HashMap<>();
                for (Object[] row : scoreRepository.findAttemptAggregatesByUsersAndDifficulties(userIds,
                                difficultyIds)) {
                        byKey.put(new AttemptKey((Long) row[0], (UUID) row[1]),
                                        new AttemptAggregate((Integer) row[2], (Integer) row[3],
                                                        (Instant) row[4]));
                }
                return byKey;
        }

        private static AttemptAggregate aggregateFor(java.util.Map<AttemptKey, AttemptAggregate> aggregates, Score s) {
                return aggregates.get(new AttemptKey(s.getUser().getId(), s.getMapDifficulty().getId()));
        }

        private static Integer maxStreakFor(java.util.Map<AttemptKey, AttemptAggregate> aggregates, Score s) {
                AttemptAggregate aggregate = aggregateFor(aggregates, s);
                return aggregate == null ? null : aggregate.maxStreak115();
        }

        private static Integer playCountFor(java.util.Map<AttemptKey, AttemptAggregate> aggregates, Score s) {
                AttemptAggregate aggregate = aggregateFor(aggregates, s);
                return aggregate == null || aggregate.playCount() == null
                                ? s.getPlayCount()
                                : aggregate.playCount();
        }

        private static Instant lastPlayedAtFor(java.util.Map<AttemptKey, AttemptAggregate> aggregates, Score s) {
                AttemptAggregate aggregate = aggregateFor(aggregates, s);
                return aggregate == null ? null : aggregate.lastPlayedAt();
        }

        private static final String ACCURACY_SORT_EXPRESSION = "CAST(s.score AS double) / s.mapDifficulty.maxScore";

        private static final String COMPLEXITY_SORT_EXPRESSION = "(SELECT mdc.complexity FROM MapDifficultyComplexity mdc "
                        + "WHERE mdc.mapDifficulty = s.mapDifficulty AND mdc.active = true)";

        private static final String MAX_STREAK_SORT_EXPRESSION = "(SELECT MAX(s2.streak115) FROM Score s2 "
                        + "WHERE s2.user = s.user AND s2.mapDifficulty = s.mapDifficulty "
                        + "AND (s2.supersedesReason IS NULL OR s2.supersedesReason <> 'Campaign attempt'))";

        private static final String PLAY_COUNT_SORT_EXPRESSION = "(SELECT MAX(s2.playCount) FROM Score s2 "
                        + "WHERE s2.user = s.user AND s2.mapDifficulty = s.mapDifficulty)";

        private static final String LAST_PLAYED_AT_SORT_EXPRESSION = "(SELECT MAX(COALESCE(s2.timeSet, s2.createdAt)) "
                        + "FROM Score s2 WHERE s2.user = s.user AND s2.mapDifficulty = s.mapDifficulty)";

        private Pageable resolveSort(Pageable pageable, Sort defaultSort) {
                Sort resolved;
                if (!pageable.getSort().isSorted()) {
                        resolved = defaultSort;
                } else {
                        resolved = Sort.unsorted();
                        for (Sort.Order order : pageable.getSort()) {
                                if ("accuracy".equalsIgnoreCase(order.getProperty())) {
                                        resolved = resolved
                                                        .and(JpaSort.unsafe(Sort.Direction.ASC,
                                                                        "(CASE WHEN (" + ACCURACY_SORT_EXPRESSION
                                                                                        + ") IS NULL THEN 1 ELSE 0 END)"))
                                                        .and(JpaSort.unsafe(order.getDirection(),
                                                                        ACCURACY_SORT_EXPRESSION));
                                } else if ("complexity".equalsIgnoreCase(order.getProperty())) {
                                        resolved = resolved
                                                        .and(JpaSort.unsafe(Sort.Direction.ASC,
                                                                        "(CASE WHEN (" + COMPLEXITY_SORT_EXPRESSION
                                                                                        + ") IS NULL THEN 1 ELSE 0 END)"))
                                                        .and(JpaSort.unsafe(order.getDirection(),
                                                                        COMPLEXITY_SORT_EXPRESSION));
                                } else if ("maxStreak115".equalsIgnoreCase(order.getProperty())) {
                                        resolved = resolved
                                                        .and(JpaSort.unsafe(Sort.Direction.ASC,
                                                                        "(CASE WHEN (" + MAX_STREAK_SORT_EXPRESSION
                                                                                        + ") IS NULL THEN 1 ELSE 0 END)"))
                                                        .and(JpaSort.unsafe(order.getDirection(),
                                                                        MAX_STREAK_SORT_EXPRESSION));
                                } else if ("playCount".equalsIgnoreCase(order.getProperty())) {
                                        resolved = resolved
                                                        .and(JpaSort.unsafe(Sort.Direction.ASC,
                                                                        "(CASE WHEN (" + PLAY_COUNT_SORT_EXPRESSION
                                                                                        + ") IS NULL THEN 1 ELSE 0 END)"))
                                                        .and(JpaSort.unsafe(order.getDirection(),
                                                                        PLAY_COUNT_SORT_EXPRESSION));
                                } else if ("lastPlayedAt".equalsIgnoreCase(order.getProperty())) {
                                        resolved = resolved.and(JpaSort.unsafe(order.getDirection(),
                                                        LAST_PLAYED_AT_SORT_EXPRESSION));
                                } else {
                                        resolved = resolved.and(Sort.by(
                                                        new Sort.Order(order.getDirection(), order.getProperty(),
                                                                        Sort.NullHandling.NULLS_LAST)));
                                }
                        }
                }
                resolved = resolved.and(Sort.by(Sort.Direction.ASC, "rank"));
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
        }

        public ScoreResponse mapToResponse(Score s) {
                Double accuracy = computeAccuracy(s.getScore(), s.getMapDifficulty().getMaxScore());
                List<UUID> modifierIds = loadModifierIds(s.getId());
                return toResponse(s, accuracy, modifierIds);
        }

        public java.util.Map<UUID, ScoreResponse> mapToResponsesByScoreId(java.util.Collection<Score> scores) {
                java.util.Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                                scores.stream().map(Score::getId).toList());
                java.util.Map<UUID, ScoreResponse> responses = new java.util.HashMap<>();
                for (Score s : scores) {
                        responses.put(s.getId(), toResponse(s,
                                        computeAccuracy(s.getScore(), s.getMapDifficulty().getMaxScore()),
                                        modifierIds.getOrDefault(s.getId(), List.of())));
                }
                return responses;
        }

        private ScoreResponse toResponse(Score s, Double accuracy, List<UUID> modifierIds) {
                User user = s.getUser();
                MapDifficulty diff = s.getMapDifficulty();
                com.accsaber.backend.model.entity.map.Map map = diff.getMap();
                return ScoreResponse.builder()
                                .id(s.getId())
                                .userId(String.valueOf(user.getId()))
                                .userName(user.getName())
                                .avatarUrl(user.getAvatarUrl())
                                .cdnAvatarUrl(user.getCdnAvatarUrl())
                                .country(user.getCountry())
                                .mapDifficultyId(diff.getId())
                                .mapId(map.getId())
                                .beatsaverCode(map.getBeatsaverCode())
                                .songHash(map.getSongHash())
                                .songName(map.getSongName())
                                .songSubName(map.getSongSubName())
                                .songAuthor(map.getSongAuthor())
                                .mapAuthor(map.getMapAuthor())
                                .coverUrl(map.getCoverUrl())
                                .cdnCoverUrl(map.getCdnCoverUrl())
                                .difficulty(diff.getDifficulty())
                                .characteristic(diff.getCharacteristic())
                                .categoryId(diff.getCategory() != null ? diff.getCategory().getId() : null)
                                .score(s.getScore())
                                .scoreNoMods(s.getScoreNoMods())
                                .accuracy(accuracy)
                                .rank(s.getRank())
                                .rankWhenSet(s.getRankWhenSet())
                                .ap(s.getAp())
                                .weightedAp(s.getWeightedAp())
                                .blScoreId(s.getBlScoreId())
                                .ssScoreId(s.getSsScoreId())
                                .maxCombo(s.getMaxCombo())
                                .badCuts(s.getBadCuts())
                                .misses(s.getMisses())
                                .wallHits(s.getWallHits())
                                .bombHits(s.getBombHits())
                                .pauses(s.getPauses())
                                .streak115(s.getStreak115())
                                .playCount(s.getPlayCount())
                                .hmd(HmdMapper.normalize(s.getHmd()))
                                .timeSet(s.getTimeSet())
                                .reweightDerivative(s.isReweightDerivative())
                                .xpGained(s.getXpGained())
                                .baseXp(xpCalculationService.getBaseXpPerScore())
                                .bonusXp(s.getXpGained() != null
                                                ?Math.max((s.getXpGained() - (double) (xpCalculationService
                                                                                .getBaseXpPerScore())), 0.0)
                                                : 0.0)
                                .active(s.isActive())
                                .partial(s.isPartial())
                                .supersedesReason(s.getSupersedesReason())
                                .modifierIds(modifierIds)
                                .createdAt(s.getCreatedAt())
                                .build();
        }
}
