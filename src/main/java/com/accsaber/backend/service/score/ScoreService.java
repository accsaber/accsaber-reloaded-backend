package com.accsaber.backend.service.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;

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
        private final ApplicationEventPublisher eventPublisher;

        @Transactional
        public ScoreResponse submit(SubmitScoreRequest request) {
                MapDifficulty difficulty = loadRankedDifficulty(request.getMapDifficultyId());
                User user = loadActiveUser(request.getUserId());

                Optional<Score> existing = scoreRepository
                                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(user.getId(), difficulty.getId());

                if (existing.isPresent() && existing.get().getScore().equals(request.getScore())) {
                        throw new ValidationException("Duplicate score: this exact score is already registered");
                }

                BigDecimal accuracy = computeAccuracy(request.getScore(), difficulty.getMaxScore());
                BigDecimal complexity = mapComplexityService.findActiveComplexity(difficulty.getId())
                                .orElseThrow(() -> new ValidationException(
                                                "No active complexity set for this map difficulty"));

                APResult apResult = apCalculationService.calculateRawAP(
                                accuracy, complexity, difficulty.getCategory().getScoreCurve());
                BigDecimal rawAp = apResult.rawAP();

                BigDecimal xpGained;
                if (existing.isPresent() && rawAp.compareTo(existing.get().getAp()) <= 0) {
                        xpGained = xpCalculationService.calculateXpForWorseScore();
                        Score history = buildScore(request, user, difficulty, rawAp, null);
                        history.setActive(false);
                        history.setSupersedesReason("Worse score");
                        history.setXpGained(xpGained);
                        scoreRepository.saveAndFlush(history);
                        updateUserXp(user, xpGained);

                        ScoreResponse worseResponse = toResponse(history,
                                        computeAccuracy(history.getScore(), difficulty.getMaxScore()),
                                        loadModifierIds(history.getId()));
                        eventPublisher.publishEvent(new ScoreSubmittedEvent(worseResponse));
                        return worseResponse;
                }

                Score supersedes = existing.orElse(null);
                if (supersedes != null) {
                        xpGained = xpCalculationService.calculateXpForImprovement(
                                        accuracy, complexity, supersedes.getXpGained());
                        supersedes.setActive(false);
                        supersedes.setSupersedesReason("Score improved");
                        scoreRepository.saveAndFlush(supersedes);
                } else {
                        xpGained = xpCalculationService.calculateXpForNewMap(accuracy, complexity);
                }

                Score newScore = buildScore(request, user, difficulty, rawAp, supersedes);
                newScore.setXpGained(xpGained);
                Score saved = scoreRepository.saveAndFlush(newScore);
                saveModifierLinks(saved, request.getModifierIds());

                updateUserXp(user, xpGained);

                statisticsService.recalculate(user.getId(), difficulty.getCategory().getId());
                rankingService.updateRankings(difficulty.getCategory().getId());
                mapDifficultyStatisticsService.recalculate(difficulty, user.getId());

                var evaluation = milestoneEvaluationService.evaluateAfterScore(user.getId(), saved);
                awardMilestoneXp(user, evaluation);

                ScoreResponse response = toResponse(saved, accuracy, loadModifierIds(saved.getId()));
                eventPublisher.publishEvent(new ScoreSubmittedEvent(response));
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
                User user = loadActiveUser(request.getUserId());

                Optional<Score> existing = scoreRepository
                                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(user.getId(), difficulty.getId());

                if (existing.isPresent() && existing.get().getScore().equals(request.getScore())) {
                        return;
                }

                BigDecimal accuracy = computeAccuracy(request.getScore(), difficulty.getMaxScore());
                APResult apResult = apCalculationService.calculateRawAP(
                                accuracy, complexity, difficulty.getCategory().getScoreCurve());
                BigDecimal rawAp = apResult.rawAP();

                BigDecimal xpGained;
                if (existing.isPresent() && rawAp.compareTo(existing.get().getAp()) <= 0) {
                        xpGained = xpCalculationService.calculateXpForWorseScore();
                        Score history = buildScore(request, user, difficulty, rawAp, null);
                        history.setActive(false);
                        history.setXpGained(xpGained);
                        scoreRepository.saveAndFlush(history);
                        updateUserXp(user, xpGained);
                        return;
                }

                Score supersedes = existing.orElse(null);
                if (supersedes != null) {
                        xpGained = xpCalculationService.calculateXpForImprovement(
                                        accuracy, complexity, supersedes.getXpGained());
                        supersedes.setActive(false);
                        scoreRepository.saveAndFlush(supersedes);
                } else {
                        xpGained = xpCalculationService.calculateXpForNewMap(accuracy, complexity);
                }

                Score newScore = buildScore(request, user, difficulty, rawAp, supersedes);
                newScore.setXpGained(xpGained);
                Score saved = scoreRepository.saveAndFlush(newScore);
                saveModifierLinks(saved, request.getModifierIds());

                updateUserXp(user, xpGained);
        }

        record RecalcResult(Long userId, UUID categoryId) {
        }

        @Transactional
        public void recalculateScore(UUID scoreId) {
                RecalcResult result = recalculateScoreForBatch(scoreId);
                if (result == null)
                        return;
                statisticsService.recalculate(result.userId(), result.categoryId());
                rankingService.updateRankings(result.categoryId());
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

                score.setActive(false);
                scoreRepository.saveAndFlush(score);

                BigDecimal xpGained = xpCalculationService.calculateXpForImprovement(
                                accuracy, complexity, score.getXpGained());

                Score recalculated = Score.builder()
                                .user(score.getUser())
                                .mapDifficulty(difficulty)
                                .score(score.getScore())
                                .scoreNoMods(score.getScoreNoMods())
                                .rank(score.getRank())
                                .rankWhenSet(score.getRankWhenSet())
                                .ap(apResult.rawAP())
                                .weightedAp(BigDecimal.ZERO)
                                .blScoreId(score.getBlScoreId())
                                .maxCombo(score.getMaxCombo())
                                .badCuts(score.getBadCuts())
                                .misses(score.getMisses())
                                .wallHits(score.getWallHits())
                                .bombHits(score.getBombHits())
                                .pauses(score.getPauses())
                                .streak115(score.getStreak115())
                                .playCount(score.getPlayCount())
                                .hmd(score.getHmd())
                                .timeSet(score.getTimeSet())
                                .reweightDerivative(true)
                                .xpGained(xpGained)
                                .supersedes(score)
                                .supersedesReason("Complexity reweight")
                                .active(true)
                                .build();

                scoreRepository.saveAndFlush(recalculated);
                copyModifierLinks(score, recalculated);
                BigDecimal oldXpGained = score.getXpGained() != null ? score.getXpGained() : BigDecimal.ZERO;
                BigDecimal xpDelta = xpGained.subtract(oldXpGained);
                if (xpDelta.compareTo(BigDecimal.ZERO) != 0) {
                        userRepository.addXp(score.getUser().getId(), xpDelta);
                }

                return new RecalcResult(score.getUser().getId(), difficulty.getCategory().getId());
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
                                .blScoreId(score.getBlScoreId())
                                .maxCombo(score.getMaxCombo())
                                .badCuts(score.getBadCuts())
                                .misses(score.getMisses())
                                .wallHits(score.getWallHits())
                                .bombHits(score.getBombHits())
                                .pauses(score.getPauses())
                                .streak115(score.getStreak115())
                                .playCount(score.getPlayCount())
                                .hmd(score.getHmd())
                                .timeSet(score.getTimeSet())
                                .reweightDerivative(score.isReweightDerivative())
                                .xpGained(newXpGained)
                                .supersedes(score)
                                .supersedesReason("XP curve update")
                                .active(true)
                                .build();

                scoreRepository.saveAndFlush(recalculated);
                copyModifierLinks(score, recalculated);
                userRepository.addXp(score.getUser().getId(), newXpGained.subtract(oldXpGained));

                return score.getUser().getId();
        }

        public Page<ScoreResponse> findByUser(Long userId, UUID categoryId, String search, Pageable pageable) {
                boolean hasSearch = search != null && !search.isBlank();
                Pageable effective = resolveSort(pageable, Sort.by(Sort.Direction.DESC, "ap"));
                Page<Score> scores;

                if (categoryId != null && hasSearch) {
                        scores = scoreRepository.findActiveByUserAndCategoryAndSongNameSearch(
                                        userId, categoryId, search.trim(), effective);
                } else if (categoryId != null) {
                        scores = scoreRepository.findActiveByUserAndCategory(userId, categoryId, effective);
                } else if (hasSearch) {
                        scores = scoreRepository.findActiveByUserAndSongNameSearch(
                                        userId, search.trim(), effective);
                } else {
                        scores = scoreRepository.findActiveByUser(userId, effective);
                }

                return scores.map(s -> toResponse(s, computeAccuracy(s.getScore(), s.getMapDifficulty().getMaxScore()),
                                loadModifierIds(s.getId())));
        }

        public Page<ScoreResponse> findByMapDifficulty(UUID mapDifficultyId, Pageable pageable) {
                MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
                if (difficulty.getMaxScore() == null || difficulty.getMaxScore() <= 0) {
                        throw new ValidationException("Map difficulty has no valid max score configured");
                }
                Pageable effective = resolveSort(pageable, Sort.by(Sort.Direction.DESC, "score"));
                return scoreRepository.findByMapDifficulty_IdAndActiveTrue(mapDifficultyId, effective)
                                .map(s -> toResponse(s, computeAccuracy(s.getScore(), difficulty.getMaxScore()),
                                                loadModifierIds(s.getId())));
        }

        private void updateUserXp(User user, BigDecimal xpGained) {
                userRepository.addXp(user.getId(), xpGained);
        }

        private void awardMilestoneXp(User user, MilestoneEvaluationService.EvaluationResult evaluation) {
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
                        updateUserXp(user, total);
                }
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
                        BigDecimal ap, Score supersedes) {
                return Score.builder()
                                .user(user)
                                .mapDifficulty(difficulty)
                                .score(req.getScore())
                                .scoreNoMods(req.getScoreNoMods())
                                .rank(req.getRank())
                                .rankWhenSet(req.getRankWhenSet())
                                .ap(ap)
                                .weightedAp(BigDecimal.ZERO)
                                .blScoreId(req.getBlScoreId())
                                .maxCombo(req.getMaxCombo())
                                .badCuts(req.getBadCuts())
                                .misses(req.getMisses())
                                .wallHits(req.getWallHits())
                                .bombHits(req.getBombHits())
                                .pauses(req.getPauses())
                                .streak115(req.getStreak115())
                                .playCount(req.getPlayCount())
                                .hmd(req.getHmd())
                                .timeSet(req.getTimeSet())
                                .supersedes(supersedes)
                                .supersedesReason(supersedes != null ? "Score improved" : null)
                                .active(true)
                                .build();
        }

        private void saveModifierLinks(Score score, List<UUID> modifierIds) {
                if (modifierIds == null || modifierIds.isEmpty())
                        return;
                List<Modifier> modifiers = modifierRepository.findAllById(modifierIds);
                if (modifiers.size() != modifierIds.size()) {
                        throw new ValidationException("One or more modifier IDs are invalid");
                }
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
                if (!pageable.getSort().isSorted()) {
                        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
                }

                Sort resolved = Sort.unsorted();
                for (Sort.Order order : pageable.getSort()) {
                        if ("accuracy".equalsIgnoreCase(order.getProperty())) {
                                resolved = resolved.and(JpaSort.unsafe(order.getDirection(), ACCURACY_SORT_EXPRESSION));
                        } else {
                                resolved = resolved.and(Sort.by(order.getDirection(), order.getProperty()));
                        }
                }
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
        }

        private ScoreResponse toResponse(Score s, BigDecimal accuracy, List<UUID> modifierIds) {
                return ScoreResponse.builder()
                                .id(s.getId())
                                .userId(String.valueOf(s.getUser().getId()))
                                .mapDifficultyId(s.getMapDifficulty().getId())
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
                                .hmd(s.getHmd())
                                .timeSet(s.getTimeSet())
                                .reweightDerivative(s.isReweightDerivative())
                                .xpGained(s.getXpGained())
                                .modifierIds(modifierIds)
                                .createdAt(s.getCreatedAt())
                                .build();
        }
}
