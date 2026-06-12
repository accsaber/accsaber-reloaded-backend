package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
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
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserRelationService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;
import com.accsaber.backend.util.HmdMapper;
import com.accsaber.backend.util.TimeRangeUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScoreService {

        private static final int ACCURACY_SCALE = 10;

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
        private final ApplicationEventPublisher eventPublisher;
        private final TransactionTemplate transactionTemplate;
        private final com.accsaber.backend.service.supporter.SupporterService supporterService;

        @Transactional
        public ScoreResponse submit(SubmitScoreRequest request) {
                acquireSubmitLock(request.getUserId(), request.getMapDifficultyId());
                MapDifficulty difficulty = loadRankedDifficulty(request.getMapDifficultyId());
                validateScoreBounds(request, difficulty);
                User user = loadActiveUser(request.getUserId());

                Optional<Score> playMatch = findRecentMatchingPlay(user.getId(), difficulty.getId(),
                                request.getScoreNoMods(), request.isPartial());
                if (playMatch.isPresent()) {
                        return backfillExistingScore(playMatch.get(), request, difficulty);
                }

                Optional<Score> existing = scoreRepository
                                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(user.getId(), difficulty.getId());

                ensurePlayCount(request, user.getId(), difficulty.getId());

                List<Modifier> modifiers = resolveModifiers(request.getModifierIds());
                Integer modifiedScore = applyModifierMultiplier(request.getScore(), modifiers);

                BigDecimal accuracy = computeAccuracy(modifiedScore, difficulty.getMaxScore());
                BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId())
                                .orElseThrow(() -> new ValidationException(
                                                "No active complexity set for this map difficulty"));

                APResult apResult = apCalculationService.calculateRawAP(
                                accuracy, complexity, difficulty.getCategory().getScoreCurve());
                BigDecimal rawAp = apResult.rawAP();

                BigDecimal xpGained;
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

                        var evaluation = milestoneEvaluationService.evaluateAfterScore(user.getId(), history);
                        if (!evaluation.completedMilestones().isEmpty()
                                        || !evaluation.completedSets().isEmpty()) {
                                awardMilestoneXp(user.getId(), evaluation);
                        }
                        campaignEvaluationService.evaluateAfterScore(user.getId(), history);

                        ScoreResponse worseResponse = toResponse(history,
                                        computeAccuracy(history.getScore(), difficulty.getMaxScore()),
                                        loadModifierIds(history.getId()));
                        eventPublisher.publishEvent(new ScoreSubmittedEvent(worseResponse));
                        return worseResponse;
                }

                Score supersedes = existing.orElse(null);
                int newRank;
                if (supersedes != null) {
                        BigDecimal oldAccuracy = computeAccuracy(supersedes.getScore(), difficulty.getMaxScore());
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

                statisticsService.recalculate(user.getId(), difficulty.getCategory().getId());
                mapDifficultyStatisticsService.recalculate(difficulty, user.getId());

                final Long userId = user.getId();
                final UUID scoreId = saved.getId();
                final ScoreResponse response = toResponse(saved, accuracy, loadModifierIds(saved.getId()));

                rankingService.updateRankingForUserAsync(difficulty.getCategory().getId(), userId, () -> {
                        transactionTemplate.executeWithoutResult(status -> {
                                Score freshScore = scoreRepository.findById(scoreId).orElse(null);
                                if (freshScore != null) {
                                        var evaluation = milestoneEvaluationService.evaluateAfterScore(userId,
                                                        freshScore);
                                        if (!evaluation.completedMilestones().isEmpty()
                                                        || !evaluation.completedSets().isEmpty()) {
                                                awardMilestoneXp(userId, evaluation);
                                        }
                                        campaignEvaluationService.evaluateAfterScore(userId, freshScore);
                                }
                                eventPublisher.publishEvent(new ScoreSubmittedEvent(response));
                        });
                });

                return response;
        }

        @Transactional
        public void submitForBackfill(SubmitScoreRequest request) {
                MapDifficulty difficulty = loadRankedDifficulty(request.getMapDifficultyId());
                BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId())
                                .orElseThrow(() -> new ValidationException(
                                                "No active complexity set for this map difficulty"));
                doSubmitForBackfill(request, difficulty, complexity);
        }

        @Transactional
        public void submitForBackfill(SubmitScoreRequest request, MapDifficulty difficulty, BigDecimal complexity) {
                doSubmitForBackfill(request, difficulty, complexity);
        }

        private void doSubmitForBackfill(SubmitScoreRequest request, MapDifficulty difficulty, BigDecimal complexity) {
                acquireSubmitLock(request.getUserId(), difficulty.getId());
                validateScoreBounds(request, difficulty);
                User user = loadUserForBackfill(request.getUserId());

                Optional<Score> playMatch = findRecentMatchingPlay(user.getId(), difficulty.getId(),
                                request.getScoreNoMods(), request.isPartial());
                if (playMatch.isPresent()) {
                        backfillExistingScore(playMatch.get(), request, difficulty);
                        return;
                }

                Optional<Score> existing = scoreRepository
                                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(user.getId(), difficulty.getId());

                ensurePlayCount(request, user.getId(), difficulty.getId());

                List<Modifier> modifiers = resolveModifiers(request.getModifierIds());
                Integer modifiedScore = applyModifierMultiplier(request.getScore(), modifiers);

                BigDecimal accuracy = computeAccuracy(modifiedScore, difficulty.getMaxScore());
                APResult apResult = apCalculationService.calculateRawAP(
                                accuracy, complexity, difficulty.getCategory().getScoreCurve());
                BigDecimal rawAp = apResult.rawAP();

                BigDecimal xpGained;
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
                        BigDecimal oldAccuracy = computeAccuracy(supersedes.getScore(), difficulty.getMaxScore());
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
                BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
                if (complexity == null)
                        return null;
                return doRecalculateScoreForBatch(score, difficulty, complexity);
        }

        @Transactional
        public RecalcResult recalculateScoreForBatch(UUID scoreId, MapDifficulty difficulty, BigDecimal complexity) {
                Score score = scoreRepository.findByIdWithUser(scoreId).orElse(null);
                if (score == null || !score.isActive())
                        return null;
                if (!score.getMapDifficulty().getId().equals(difficulty.getId())) {
                        throw new IllegalArgumentException(
                                        "Provided difficulty does not match score's map difficulty");
                }
                return doRecalculateScoreForBatch(score, difficulty, complexity);
        }

        private RecalcResult doRecalculateScoreForBatch(Score score, MapDifficulty difficulty, BigDecimal complexity) {
                BigDecimal accuracy = computeAccuracy(score.getScore(), difficulty.getMaxScore());
                APResult apResult = apCalculationService.calculateRawAP(
                                accuracy, complexity, difficulty.getCategory().getScoreCurve());

                if (apResult.rawAP().compareTo(score.getAp()) == 0)
                        return null;

                BigDecimal oldXp = score.getXpGained() != null ? score.getXpGained() : BigDecimal.ZERO;
                score.setActive(false);
                score.setXpGained(BigDecimal.ZERO);
                scoreRepository.saveAndFlush(score);

                Score recalculated = Score.builder()
                                .user(score.getUser())
                                .mapDifficulty(difficulty)
                                .score(score.getScore())
                                .scoreNoMods(score.getScoreNoMods())
                                .rank(score.getRank())
                                .rankWhenSet(score.getRankWhenSet())
                                .ap(apResult.rawAP())
                                .weightedAp(BigDecimal.ZERO)
                                .reweightDerivative(true)
                                .xpGained(BigDecimal.ZERO)
                                .supersedes(score)
                                .supersedesReason("Complexity reweight")
                                .active(true)
                                .build();
                ScorePayloadFields.copyAll(score, recalculated);

                scoreRepository.saveAndFlush(recalculated);
                copyModifierLinks(score, recalculated);
                if (oldXp.compareTo(BigDecimal.ZERO) > 0) {
                        levelUpAwardService.addXp(score.getUser().getId(), oldXp.negate());
                }

                return new RecalcResult(score.getUser().getId(), difficulty.getCategory().getId(), difficulty.getId());
        }

        @Transactional
        public Long recalculateScoreXpForBatch(UUID scoreId) {
                Score score = scoreRepository.findByIdWithUser(scoreId).orElse(null);
                if (score == null || !score.isActive())
                        return null;
                MapDifficulty difficulty = score.getMapDifficulty();
                if (difficulty.getMaxScore() == null || difficulty.getMaxScore() <= 0)
                        return null;
                BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId()).orElse(null);
                if (complexity == null)
                        return null;
                return doRecalculateScoreXpForBatch(score, difficulty, complexity);
        }

        @Transactional
        public Long recalculateScoreXpForBatch(UUID scoreId, MapDifficulty difficulty, BigDecimal complexity) {
                Score score = scoreRepository.findByIdWithUser(scoreId).orElse(null);
                if (score == null || !score.isActive())
                        return null;
                if (!score.getMapDifficulty().getId().equals(difficulty.getId())) {
                        throw new IllegalArgumentException(
                                        "Provided difficulty does not match score's map difficulty");
                }
                return doRecalculateScoreXpForBatch(score, difficulty, complexity);
        }

        private Long doRecalculateScoreXpForBatch(Score score, MapDifficulty difficulty, BigDecimal complexity) {
                BigDecimal accuracy = computeAccuracy(score.getScore(), difficulty.getMaxScore());
                BigDecimal newXpGained = xpCalculationService.calculateXpForNewMap(accuracy, complexity);
                BigDecimal oldXpGained = score.getXpGained() != null ? score.getXpGained() : BigDecimal.ZERO;

                if (newXpGained.compareTo(oldXpGained) == 0)
                        return null;

                score.setActive(false);
                scoreRepository.saveAndFlush(score);

                Score recalculated = Score.builder()
                                .user(score.getUser())
                                .mapDifficulty(difficulty)
                                .score(score.getScore())
                                .scoreNoMods(score.getScoreNoMods())
                                .rank(score.getRank())
                                .rankWhenSet(score.getRankWhenSet())
                                .ap(score.getAp())
                                .weightedAp(score.getWeightedAp())
                                .reweightDerivative(score.isReweightDerivative())
                                .xpGained(newXpGained)
                                .supersedes(score)
                                .supersedesReason("XP curve update")
                                .active(true)
                                .build();
                ScorePayloadFields.copyAll(score, recalculated);

                scoreRepository.saveAndFlush(recalculated);
                copyModifierLinks(score, recalculated);
                levelUpAwardService.addXp(score.getUser().getId(), newXpGained.subtract(oldXpGained));

                return score.getUser().getId();
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
                boolean hasSearch = search != null && !search.isBlank();
                Pageable effective = resolveSort(pageable, Sort.by(Sort.Direction.DESC, "ap"));
                Page<Score> scores;

                if (categoryId != null && hasSearch) {
                        scores = scoreRepository.findActiveByUserAndCategoryAndSongNameSearch(
                                        resolvedUserId, categoryId, search.trim(), effective);
                } else if (categoryId != null) {
                        scores = scoreRepository.findActiveByUserAndCategory(resolvedUserId, categoryId, effective);
                } else if (hasSearch) {
                        scores = scoreRepository.findActiveByUserAndSongNameSearch(
                                        resolvedUserId, search.trim(), effective);
                } else {
                        scores = scoreRepository.findActiveByUser(resolvedUserId, effective);
                }

                return scores.map(s -> toResponse(s, computeAccuracy(s.getScore(), s.getMapDifficulty().getMaxScore()),
                                loadModifierIds(s.getId())));
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
                        scores = scoreRepository.findActiveByUsersAndCategoryAndSongNameSearch(
                                        userIds, categoryId, search.trim(), effective);
                } else if (categoryId != null) {
                        scores = scoreRepository.findActiveByUsersAndCategory(userIds, categoryId, effective);
                } else if (hasSearch) {
                        scores = scoreRepository.findActiveByUsersAndSongNameSearch(
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

                return scores.map(s -> {
                        Integer maxScore = s.getMapDifficulty().getMaxScore();
                        ScoreResponse base = toResponse(s, computeAccuracy(s.getScore(), maxScore),
                                        loadModifierIds(s.getId()));
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
                return scoreRepository.findByMapDifficulty_IdAndActiveTrue(mapDifficultyId, effective)
                                .map(s -> toResponse(s, computeAccuracy(s.getScore(), difficulty.getMaxScore()),
                                                loadModifierIds(s.getId())));
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
                return scores.map(s -> toResponse(s,
                                computeAccuracy(s.getScore(), difficulty.getMaxScore()),
                                loadModifierIds(s.getId()))
                                .toBuilder().supporterTier(tiers.get(s.getUser().getId())).build());
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
                List<ScoreResponse> aboveScores = scores.subList(0, playerIndex).stream()
                                .map(s -> toResponse(s, computeAccuracy(s.getScore(), maxScore),
                                                loadModifierIds(s.getId())))
                                .toList();
                ScoreResponse player = toResponse(scores.get(playerIndex),
                                computeAccuracy(scores.get(playerIndex).getScore(), maxScore),
                                loadModifierIds(scores.get(playerIndex).getId()));
                List<ScoreResponse> belowScores = scores.subList(playerIndex + 1, scores.size()).stream()
                                .map(s -> toResponse(s, computeAccuracy(s.getScore(), maxScore),
                                                loadModifierIds(s.getId())))
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

                return scores.stream()
                                .map(s -> toResponse(s,
                                                computeAccuracy(s.getScore(),
                                                                s.getMapDifficulty().getMaxScore()),
                                                loadModifierIds(s.getId())))
                                .toList();
        }

        private void updateUserXp(Long userId, BigDecimal xpGained) {
                levelUpAwardService.addXp(userId, xpGained);
        }

        private void awardMilestoneXp(Long userId, MilestoneEvaluationService.EvaluationResult evaluation) {
                if (evaluation.completedMilestones().isEmpty() && evaluation.completedSets().isEmpty())
                        return;

                BigDecimal milestoneXp = evaluation.completedMilestones().stream()
                                .map(Milestone::getXp)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal setXp = evaluation.completedSets().stream()
                                .map(MilestoneSet::getSetBonusXp)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal total = milestoneXp.add(setXp);
                if (total.compareTo(BigDecimal.ZERO) > 0) {
                        updateUserXp(userId, total);
                }
        }

        private void validateScoreBounds(SubmitScoreRequest request, MapDifficulty difficulty) {
                Integer max = difficulty.getMaxScore();
                if (max == null || max <= 0) {
                        return;
                }
                if (request.getScoreNoMods() != null && request.getScoreNoMods() > max) {
                        throw new ValidationException("scoreNoMods exceeds the map's maxScore");
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

        private Optional<Score> findRecentMatchingPlay(Long userId, UUID mapDifficultyId, Integer scoreNoMods,
                        boolean partial) {
                if (scoreNoMods == null) {
                        return Optional.empty();
                }
                Instant since = Instant.now().minus(Duration.ofDays(1));
                List<Score> matches = scoreRepository.findRecentMatchingPlay(userId, mapDifficultyId, scoreNoMods,
                                partial, since, PageRequest.of(0, 1));
                return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
        }

        private ScoreResponse backfillExistingScore(Score existing, SubmitScoreRequest request,
                        MapDifficulty difficulty) {
                if (ScorePayloadFields.mergeNullOnly(existing, request)) {
                        scoreRepository.saveAndFlush(existing);
                        Long userId = existing.getUser().getId();
                        var evaluation = milestoneEvaluationService.evaluateAfterScore(userId, existing);
                        if (!evaluation.completedMilestones().isEmpty()
                                        || !evaluation.completedSets().isEmpty()) {
                                awardMilestoneXp(userId, evaluation);
                        }
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

        private BigDecimal computeAccuracy(Integer score, Integer maxScore) {
                return BigDecimal.valueOf(score).divide(BigDecimal.valueOf(maxScore), ACCURACY_SCALE,
                                RoundingMode.HALF_UP);
        }

        private Score buildScore(SubmitScoreRequest req, User user, MapDifficulty difficulty,
                        Integer modifiedScore, BigDecimal ap, Score supersedes) {
                Score score = Score.builder()
                                .user(user)
                                .mapDifficulty(difficulty)
                                .score(modifiedScore)
                                .scoreNoMods(req.getScoreNoMods())
                                .rank(req.getRank())
                                .rankWhenSet(req.getRankWhenSet())
                                .ap(ap)
                                .weightedAp(BigDecimal.ZERO)
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
                BigDecimal combined = modifiers.stream()
                                .map(Modifier::getMultiplier)
                                .reduce(BigDecimal.ONE, BigDecimal::multiply);
                return combined.multiply(BigDecimal.valueOf(baseScore))
                                .setScale(0, RoundingMode.HALF_UP).intValue();
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

        private static final String ACCURACY_SORT_EXPRESSION = "CAST(s.score AS double) / s.mapDifficulty.maxScore";

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
                BigDecimal accuracy = computeAccuracy(s.getScore(), s.getMapDifficulty().getMaxScore());
                List<UUID> modifierIds = loadModifierIds(s.getId());
                return toResponse(s, accuracy, modifierIds);
        }

        private ScoreResponse toResponse(Score s, BigDecimal accuracy, List<UUID> modifierIds) {
                User user = s.getUser();
                MapDifficulty diff = s.getMapDifficulty();
                com.accsaber.backend.model.entity.map.Map map = diff.getMap();
                return ScoreResponse.builder()
                                .id(s.getId())
                                .userId(String.valueOf(user.getId()))
                                .userName(user.getName())
                                .avatarUrl(user.getAvatarUrl())
                                .country(user.getCountry())
                                .mapDifficultyId(diff.getId())
                                .mapId(map.getId())
                                .beatsaverCode(map.getBeatsaverCode())
                                .songHash(map.getSongHash())
                                .songName(map.getSongName())
                                .songAuthor(map.getSongAuthor())
                                .mapAuthor(map.getMapAuthor())
                                .coverUrl(map.getCoverUrl())
                                .cdnCoverUrl(map.getCdnCoverUrl())
                                .difficulty(diff.getDifficulty())
                                .characteristic(diff.getCharacteristic())
                                .categoryId(diff.getCategory().getId())
                                .score(s.getScore())
                                .scoreNoMods(s.getScoreNoMods())
                                .accuracy(accuracy)
                                .rank(s.getRank())
                                .rankWhenSet(s.getRankWhenSet())
                                .ap(s.getAp())
                                .weightedAp(s.getWeightedAp())
                                .blScoreId(s.getBlScoreId())
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
                                .baseXp(BigDecimal.valueOf(xpCalculationService.getBaseXpPerScore()))
                                .bonusXp(s.getXpGained() != null
                                                ? s.getXpGained()
                                                                .subtract(BigDecimal.valueOf(xpCalculationService
                                                                                .getBaseXpPerScore()))
                                                                .max(BigDecimal.ZERO)
                                                : BigDecimal.ZERO)
                                .active(s.isActive())
                                .partial(s.isPartial())
                                .supersedesReason(s.getSupersedesReason())
                                .modifierIds(modifierIds)
                                .createdAt(s.getCreatedAt())
                                .build();
        }
}
