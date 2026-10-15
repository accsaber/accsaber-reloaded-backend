package com.accsaber.backend.service.score;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.APResult;
import com.accsaber.backend.model.dto.request.score.PluginSubmitRequest;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.Modifier;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ScoreSubmittedEvent;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.campaign.CampaignEvaluationService;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.item.LevelUpAwardService;
import com.accsaber.backend.service.item.StrangeTrackingService;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.milestone.MilestoneEvaluationService;
import com.accsaber.backend.service.staff.JwtService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;
import com.accsaber.backend.util.MapDifficultyMetrics;
import com.accsaber.backend.util.Rounding;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScoreService {

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
    private final LevelUpAwardService levelUpAwardService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final ModifierCacheService modifierCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final StrangeTrackingService strangeTrackingService;
    private final ScoreQueryService scoreQueryService;
    private final SubmitNonceService nonceService;
    private final SubmitRateLimitService rateLimitService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<ScoreResponse> submitFromPlugin(PluginSubmitRequest body, PlayerUserDetails principal) {
        PlayerUserDetails.require(principal);
        if (JwtService.SCOPE_WEB.equals(principal.getTokenScope())) {
            throw new ForbiddenException(
                    "Score submission requires a game session; website tokens cannot submit scores");
        }
        if (!nonceService.tryConsume(principal.getUserId(), body.getNonce())) {
            throw new ConflictException("Duplicate submission: nonce already used");
        }
        if (!rateLimitService.tryAcquire(principal.getUserId())) {
            throw new TooManyRequestsException("Submitting too fast; wait 60s between scores");
        }
        SubmitScoreRequest request = toSubmitRequest(body, principal.getUserId());
        return transactionTemplate.execute(status -> submitPlayer(request));
    }

    private SubmitScoreRequest toSubmitRequest(PluginSubmitRequest body, Long userId) {
        SubmitScoreRequest req = new SubmitScoreRequest();
        req.setUserId(userId);
        req.setMapDifficultyId(body.getMapDifficultyId());
        req.setScore(body.getScore());
        req.setScoreNoMods(body.getScoreNoMods());
        req.setRank(0);
        req.setRankWhenSet(0);
        req.setMaxCombo(body.getMaxCombo());
        req.setBadCuts(body.getBadCuts());
        req.setMisses(body.getMisses());
        req.setWallHits(body.getWallHits());
        req.setBombHits(body.getBombHits());
        req.setPauses(body.getPauses());
        req.setStreak115(body.getStreak115());
        req.setHmd(body.getHmd());
        Instant now = Instant.now();
        req.setTimeSet(body.getTimeSet() == null || body.getTimeSet().isAfter(now) ? now : body.getTimeSet());
        req.setModifierIds(resolveModifierCodes(body.getModifierCodes()));
        req.setPartial(body.isPartial());
        return req;
    }

    private List<UUID> resolveModifierCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Map<String, UUID> codeToId = modifierCacheService.getModifierCodeToId();
        List<UUID> ids = new ArrayList<>();
        for (String code : codes) {
            if (code == null) continue;
            UUID id = codeToId.get(code.trim());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Transactional
    public ScoreResponse submit(SubmitScoreRequest request) {
        acquireSubmitLock(request.getUserId(), request.getMapDifficultyId());
        MapDifficulty difficulty = loadRankedDifficulty(request.getMapDifficultyId());
        validateScoreBounds(request, difficulty, true);
        User user = loadActiveUser(request.getUserId());

        Optional<Score> playMatch = findRecentMatchingPlay(user.getId(), difficulty.getId(), request, false);
        if (playMatch.isPresent()) {
            return backfillExistingScore(playMatch.get(), request);
        }

        Optional<Score> existing = scoreRepository
                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(user.getId(), difficulty.getId());

        ensurePlayCount(request, user.getId(), difficulty.getId());

        List<Modifier> modifiers = resolveModifiers(request.getModifierIds());
        Integer modifiedScore = applyModifierMultiplier(request.getScore(), modifiers);

        Double accuracy = APCalculationService.computeAccuracy(modifiedScore, difficulty.getMaxScore());
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

            ScoreResponse worseResponse = scoreQueryService.mapToResponse(history);
            eventPublisher.publishEvent(
                    new ScoreSubmittedEvent(withMapMetadata(worseResponse, difficulty)));
            return worseResponse;
        }

        Score supersedes = existing.orElse(null);
        int newRank;
        if (supersedes != null) {
            Double oldAccuracy = APCalculationService.computeAccuracy(supersedes.getScore(), difficulty.getMaxScore());
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
        final ScoreResponse response = scoreQueryService.mapToResponse(saved);

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

        Optional<Score> attemptMatch = findRecentMatchingPlay(user.getId(), difficulty.getId(), request, true);
        if (attemptMatch.isPresent()) {
            Score existing = attemptMatch.get();
            if (ScorePayloadFields.merge(existing, request)) {
                scoreRepository.saveAndFlush(existing);
            }
            campaignEvaluationService.evaluateAfterScore(user.getId(), existing);
            return Optional.of(scoreQueryService.mapToResponse(existing));
        }

        Score saved = saveCampaignAttempt(request, user, difficulty);

        campaignEvaluationService.evaluateAfterScore(user.getId(), saved);

        return Optional.of(scoreQueryService.mapToResponse(saved));
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
        saveCampaignAttempt(request, user, difficulty);
    }

    private Score saveCampaignAttempt(SubmitScoreRequest request, User user, MapDifficulty difficulty) {
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
        return saved;
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
    public void submitForBackfill(SubmitScoreRequest request, MapDifficulty difficulty, Double complexity) {
        acquireSubmitLock(request.getUserId(), difficulty.getId());
        validateScoreBounds(request, difficulty, false);
        User user = loadUserForBackfill(request.getUserId());

        Optional<Score> playMatch = findRecentMatchingPlay(user.getId(), difficulty.getId(), request, false);
        if (playMatch.isPresent()) {
            backfillExistingScore(playMatch.get(), request);
            return;
        }

        Optional<Score> existing = scoreRepository
                .findByUser_IdAndMapDifficulty_IdAndActiveTrue(user.getId(), difficulty.getId());

        ensurePlayCount(request, user.getId(), difficulty.getId());

        List<Modifier> modifiers = resolveModifiers(request.getModifierIds());
        Integer modifiedScore = applyModifierMultiplier(request.getScore(), modifiers);

        Double accuracy = APCalculationService.computeAccuracy(modifiedScore, difficulty.getMaxScore());
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
            Double oldAccuracy = APCalculationService.computeAccuracy(supersedes.getScore(), difficulty.getMaxScore());
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
    public RecalcResult recalculateScoreForBatch(UUID scoreId, MapDifficulty difficulty, Double complexity) {
        Score score = scoreRepository.findByIdWithUser(scoreId).orElse(null);
        if (score == null || !score.isActive())
            return null;
        if (!score.getMapDifficulty().getId().equals(difficulty.getId())) {
            throw new IllegalArgumentException(
                    "Provided difficulty does not match score's map difficulty");
        }
        Double accuracy = APCalculationService.computeAccuracy(score.getScore(), difficulty.getMaxScore());
        APResult apResult = apCalculationService.calculateRawAP(
                accuracy, complexity, difficulty.getCategory().getScoreCurve());

        if (apResult.rawAP() == score.getAp())
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
        modifierLinkRepository.copyLinks(score.getId(), recalculated.getId());
        if (oldXp.compareTo(0.0) > 0) {
            levelUpAwardService.addXp(score.getUser().getId(), -oldXp);
        }

        return new RecalcResult(score.getUser().getId(), difficulty.getCategory().getId(), difficulty.getId());
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
            SubmitScoreRequest request, boolean campaignAttempt) {
        if (request.getScoreNoMods() == null) {
            return Optional.empty();
        }
        boolean partial = !campaignAttempt && request.isPartial();
        Instant playAt = partial || request.getTimeSet() == null
                ? Instant.EPOCH
                : request.getTimeSet();
        List<Score> matches = scoreRepository.findRecentMatchingPlay(userId, mapDifficultyId,
                request.getScoreNoMods(), partial, campaignAttempt,
                Instant.now().minus(Duration.ofDays(1)),
                playAt.minus(PLAY_MATCH_WINDOW), playAt.plus(PLAY_MATCH_WINDOW),
                PageRequest.of(0, 1));
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    private ScoreResponse backfillExistingScore(Score existing, SubmitScoreRequest request) {
        if (ScorePayloadFields.merge(existing, request)) {
            scoreRepository.saveAndFlush(existing);
            milestoneEvaluationService.evaluateAfterScore(existing.getUser().getId(), existing);
        }
        return scoreQueryService.mapToResponse(existing);
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
}
