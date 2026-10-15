package com.accsaber.backend.service.score;

import static com.accsaber.backend.service.score.APCalculationService.computeAccuracy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.player.PinnedScoreResponse;
import com.accsaber.backend.model.dto.response.score.ApCurveResponse;
import com.accsaber.backend.model.dto.response.score.MyScoreSummary;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.score.ScoresAroundResponse;
import com.accsaber.backend.model.dto.response.score.UserScoreSummaryResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.repository.SortAliases;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserPinnedScoreRepository;
import com.accsaber.backend.service.clan.ClanRefCache;
import com.accsaber.backend.service.map.MapDifficultyComplexityService;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserRelationService;
import com.accsaber.backend.service.skill.SkillService;
import com.accsaber.backend.service.supporter.SupporterService;
import com.accsaber.backend.util.HmdMapper;
import com.accsaber.backend.util.SqlValues;
import com.accsaber.backend.util.Texts;
import com.accsaber.backend.util.TimeRangeUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScoreQueryService {

    private static final int MAX_AP_CURVE_POINTS = 100;
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

    private static final Map<String, String> NULLS_LAST_SORT_EXPRESSIONS = Map.of(
            "accuracy", ACCURACY_SORT_EXPRESSION,
            "complexity", COMPLEXITY_SORT_EXPRESSION,
            "maxstreak115", MAX_STREAK_SORT_EXPRESSION,
            "playcount", PLAY_COUNT_SORT_EXPRESSION);

    private final ScoreRepository scoreRepository;
    private final ScoreModifierLinkRepository modifierLinkRepository;
    private final UserPinnedScoreRepository userPinnedScoreRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyComplexityService mapComplexityService;
    private final XPCalculationService xpCalculationService;
    private final DuplicateUserService duplicateUserService;
    private final UserRelationService userRelationService;
    private final SkillService skillService;
    private final SupporterService supporterService;

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

    public List<ApCurveResponse> findApCurves(Long userId, int limit) {
        Map<UUID, ApCurveResponse> curves = new LinkedHashMap<>();
        for (Object[] row : scoreRepository.findTopApByCategory(duplicateUserService.resolvePrimaryUserId(userId),
                Math.clamp(limit, 1, MAX_AP_CURVE_POINTS))) {
            curves.computeIfAbsent(SqlValues.toUuid(row[0]),
                    id -> new ApCurveResponse(id, (String) row[1], new ArrayList<>()))
                    .ap().add(SqlValues.toDouble(row[2]));
        }
        return List.copyOf(curves.values());
    }

    public Page<ScoreResponse> findByUser(Long userId, UUID categoryId, String search, Pageable pageable) {
        Long resolvedUserId = duplicateUserService.resolvePrimaryUserId(userId);
        Page<Score> scores = queryActiveByUser(resolvedUserId, categoryId, search,
                resolveSort(pageable, Sort.by(Sort.Direction.DESC, "ap")));

        Map<UUID, ScoreResponse> responses = mapToResponsesByScoreId(scores.getContent());
        Map<AttemptKey, AttemptAggregate> aggregates = loadAttemptAggregatesBatch(
                scores.getContent());
        return scores.map(s -> responses.get(s.getId())
                .toBuilder()
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

        Map<UUID, MapDifficulty> byId = mapDifficultyRepository
                .findAllByIdInAndActiveTrueWithMapAndCategory(orderedIds).stream()
                .collect(Collectors.toMap(MapDifficulty::getId, d -> d));
        return orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private Page<Score> queryActiveByUser(Long resolvedUserId, UUID categoryId, String search, Pageable pageable) {
        return scoreRepository.findActiveByUsers(List.of(resolvedUserId), categoryId, Texts.trimToNull(search),
                pageable);
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
        Page<Score> scores = scoreRepository.findActiveByUsers(userIds, categoryId, Texts.trimToNull(search),
                effective);

        List<UUID> difficultyIds = scores.getContent().stream()
                .map(s -> s.getMapDifficulty().getId())
                .distinct()
                .toList();
        Map<UUID, Score> myByDifficulty = difficultyIds.isEmpty()
                ? Map.of()
                : scoreRepository.findActiveByUserAndMapDifficultyIdIn(viewerUserId, difficultyIds)
                        .stream()
                        .collect(Collectors.toMap(
                                vs -> vs.getMapDifficulty().getId(),
                                Function.identity(),
                                (a, b) -> a));

        Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
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

    public Page<ScoreResponse> findLeaderboardByMapDifficulty(UUID mapDifficultyId, String country,
            String search, Collection<Long> userIdFilter, Pageable pageable) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));
        if (difficulty.getMaxScore() == null || difficulty.getMaxScore() <= 0) {
            throw new ValidationException("Map difficulty has no valid max score configured");
        }
        Pageable effective = resolveSort(pageable, Sort.by(Sort.Direction.ASC, "rank"));
        if (userIdFilter != null && userIdFilter.isEmpty()) {
            return Page.empty(effective);
        }
        String countryFilter = country == null || country.isBlank() ? null : country.toUpperCase();
        Page<Score> scores = userIdFilter != null
                ? scoreRepository.findByMapDifficultyIdAndActiveTrueWithUserFilteredByUserIds(
                        mapDifficultyId, userIdFilter, countryFilter, Texts.trimToNull(search),
                        effective)
                : scoreRepository.findLeaderboardByMapDifficulty(
                        mapDifficultyId, countryFilter, Texts.trimToNull(search), effective);
        List<Long> userIds = scores.getContent().stream()
                .map(s -> s.getUser().getId())
                .toList();
        Map<Long, String> tiers = supporterService.findCurrentTiersByUserIds(userIds);
        Map<Long, Double> skillLevels = skillService.findSkillLevelsByUserIds(
                difficulty.getCategory() != null ? difficulty.getCategory().getId() : null, userIds);
        Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                scores.getContent().stream().map(Score::getId).toList());
        Map<AttemptKey, AttemptAggregate> aggregates = loadAttemptAggregatesBatch(
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

        List<Score> scores = scoreRepository.findLeaderboardByMapDifficulty(mapDifficultyId, null, null,
                PageRequest.of(0, fetchSize, Sort.by(Sort.Direction.DESC, "score")))
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
        Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                scores.stream().map(Score::getId).toList());
        Map<AttemptKey, AttemptAggregate> aggregates = loadAttemptAggregatesBatch(scores);
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

        Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                scores.stream().map(Score::getId).toList());
        return scores.stream()
                .map(s -> toResponse(s,
                        computeAccuracy(s.getScore(),
                                s.getMapDifficulty().getMaxScore()),
                        modifierIds.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    public ScoreResponse mapToResponse(Score s) {
        Double accuracy = computeAccuracy(s.getScore(), s.getMapDifficulty().getMaxScore());
        List<UUID> modifierIds = loadModifierIds(s.getId());
        return toResponse(s, accuracy, modifierIds);
    }

    public List<PinnedScoreResponse> findPinnedScores(Long userId) {
        var pins = userPinnedScoreRepository.findActiveByUserIdWithScoreGraph(
                duplicateUserService.resolvePrimaryUserId(userId));
        var scores = mapToResponsesByScoreId(pins.stream().map(pin -> pin.getScore()).toList());
        return pins.stream()
                .map(pin -> PinnedScoreResponse.builder()
                        .score(scores.get(pin.getScore().getId()))
                        .comment(pin.getComment())
                        .build())
                .toList();
    }

    public Map<UUID, ScoreResponse> mapToResponsesByScoreId(Collection<Score> scores) {
        Map<UUID, List<UUID>> modifierIds = loadModifierIdsBatch(
                scores.stream().map(Score::getId).toList());
        Map<UUID, Double> complexities = mapComplexityService.findActiveComplexitiesForDifficulties(
                scores.stream().map(s -> s.getMapDifficulty().getId()).distinct().toList());
        Map<UUID, ScoreResponse> responses = new HashMap<>();
        for (Score s : scores) {
            responses.put(s.getId(), toResponse(s,
                    computeAccuracy(s.getScore(), s.getMapDifficulty().getMaxScore()),
                    modifierIds.getOrDefault(s.getId(), List.of()))
                    .toBuilder()
                    .complexity(complexities.get(s.getMapDifficulty().getId()))
                    .build());
        }
        return responses;
    }

    private List<UUID> loadModifierIds(UUID scoreId) {
        return modifierLinkRepository.findByScore_Id(scoreId).stream()
                .map(l -> l.getModifier().getId())
                .toList();
    }

    private Map<UUID, List<UUID>> loadModifierIdsBatch(Collection<UUID> scoreIds) {
        if (scoreIds.isEmpty()) {
            return Map.of();
        }
        return modifierLinkRepository.findByScore_IdIn(scoreIds).stream()
                .collect(Collectors.groupingBy(
                        l -> l.getScore().getId(),
                        Collectors.mapping(
                                l -> l.getModifier().getId(),
                                Collectors.toList())));
    }

    private record AttemptKey(Long userId, UUID mapDifficultyId) {
    }

    private record AttemptAggregate(Integer maxStreak115, Integer playCount, Instant lastPlayedAt) {
    }

    private Map<AttemptKey, AttemptAggregate> loadAttemptAggregatesBatch(Collection<Score> scores) {
        if (scores.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = scores.stream().map(s -> s.getUser().getId()).distinct().toList();
        List<UUID> difficultyIds = scores.stream().map(s -> s.getMapDifficulty().getId()).distinct().toList();
        Map<AttemptKey, AttemptAggregate> byKey = new HashMap<>();
        for (Object[] row : scoreRepository.findAttemptAggregatesByUsersAndDifficulties(userIds,
                difficultyIds)) {
            byKey.put(new AttemptKey((Long) row[0], (UUID) row[1]),
                    new AttemptAggregate((Integer) row[2], (Integer) row[3],
                            (Instant) row[4]));
        }
        return byKey;
    }

    private static AttemptAggregate aggregateFor(Map<AttemptKey, AttemptAggregate> aggregates, Score s) {
        return aggregates.get(new AttemptKey(s.getUser().getId(), s.getMapDifficulty().getId()));
    }

    private static Integer maxStreakFor(Map<AttemptKey, AttemptAggregate> aggregates, Score s) {
        AttemptAggregate aggregate = aggregateFor(aggregates, s);
        return aggregate == null ? null : aggregate.maxStreak115();
    }

    private static Integer playCountFor(Map<AttemptKey, AttemptAggregate> aggregates, Score s) {
        AttemptAggregate aggregate = aggregateFor(aggregates, s);
        return aggregate == null || aggregate.playCount() == null
                ? s.getPlayCount()
                : aggregate.playCount();
    }

    private static Instant lastPlayedAtFor(Map<AttemptKey, AttemptAggregate> aggregates, Score s) {
        AttemptAggregate aggregate = aggregateFor(aggregates, s);
        return aggregate == null ? null : aggregate.lastPlayedAt();
    }

    private Pageable resolveSort(Pageable pageable, Sort defaultSort) {
        Sort resolved;
        if (!pageable.getSort().isSorted()) {
            resolved = defaultSort;
        } else {
            resolved = Sort.unsorted();
            for (Sort.Order order : pageable.getSort()) {
                resolved = resolved.and(resolveOrder(order));
            }
        }
        resolved = resolved.and(Sort.by(Sort.Direction.ASC, "rank"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
    }

    private static Sort resolveOrder(Sort.Order order) {
        String property = order.getProperty().toLowerCase(Locale.ROOT);
        String expression = NULLS_LAST_SORT_EXPRESSIONS.get(property);
        if (expression != null) {
            return SortAliases.nullsLastUnsafe(order.getDirection(), "(" + expression + ")", expression);
        }
        if ("lastplayedat".equals(property)) {
            return JpaSort.unsafe(order.getDirection(), LAST_PLAYED_AT_SORT_EXPRESSION);
        }
        return Sort.by(new Sort.Order(order.getDirection(), order.getProperty(), Sort.NullHandling.NULLS_LAST));
    }

    private ScoreResponse toResponse(Score s, Double accuracy, List<UUID> modifierIds) {
        User user = s.getUser();
        MapDifficulty diff = s.getMapDifficulty();
        com.accsaber.backend.model.entity.map.Map map = diff.getMap();
        return ScoreResponse.builder()
                .id(s.getId())
                .userId(String.valueOf(user.getId()))
                .clan(ClanRefCache.forUser(user.getId()))
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
                        ? Math.max(s.getXpGained() - xpCalculationService.getBaseXpPerScore(), 0.0)
                        : 0.0)
                .active(s.isActive())
                .partial(s.isPartial())
                .supersedesReason(s.getSupersedesReason())
                .modifierIds(modifierIds)
                .createdAt(s.getCreatedAt())
                .build();
    }
}
