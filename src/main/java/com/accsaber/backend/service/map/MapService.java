package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.map.CreateMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapStatusRequest;
import com.accsaber.backend.model.dto.response.map.MapComplexityHistoryResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyStatisticsResponse;
import com.accsaber.backend.model.dto.response.map.MapResponse;
import com.accsaber.backend.model.dto.response.map.RankedDifficultyResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.VoteType;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.repository.map.StaffMapVoteRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.service.score.ScoreIngestionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapService {

    private static final Logger log = LoggerFactory.getLogger(MapService.class);

    private final MapRepository mapRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final CategoryRepository categoryRepository;
    private final BatchRepository batchRepository;
    private final MapDifficultyComplexityService complexityService;
    private final MapDifficultyStatisticsService statisticsService;
    private final StaffUserRepository staffUserRepository;
    private final StaffMapVoteRepository voteRepository;
    private final ScoreIngestionService scoreIngestionService;

    record StaffInfo(String username, String avatarUrl) {
    }

    record VoteSummary(int rankUpvotes, int rankDownvotes, int criteriaUpvotes, int criteriaDownvotes,
            VoteType headCriteriaVote, int reweightUpvotes, int reweightDownvotes,
            int unrankUpvotes, int unrankDownvotes) {
    }

    private static final VoteSummary EMPTY_SUMMARY = new VoteSummary(0, 0, 0, 0, null, 0, 0, 0, 0);

    public Page<MapResponse> findAll(UUID categoryId, MapDifficultyStatus status, String search, Pageable pageable) {
        boolean hasSearch = search != null && !search.isBlank();
        Pageable effective = resolveMapSort(pageable);
        Page<Map> maps = hasSearch
                ? mapRepository.findByDifficultyFiltersWithSearch(categoryId, status, search.trim(), effective)
                : mapRepository.findByDifficultyFilters(categoryId, status, effective);
        if (maps.isEmpty())
            return maps.map(m -> toMapResponse(m, List.of()));

        List<UUID> mapIds = maps.getContent().stream().map(Map::getId).toList();
        List<MapDifficulty> allDifficulties = mapDifficultyRepository.findByMapIdsWithFilters(mapIds, categoryId,
                status);
        java.util.Map<UUID, List<MapDifficulty>> byMap = allDifficulties.stream()
                .collect(Collectors.groupingBy(d -> d.getMap().getId()));

        List<UUID> difficultyIds = allDifficulties.stream().map(MapDifficulty::getId).toList();
        java.util.Map<UUID, BigDecimal> complexities = complexityService
                .findActiveComplexitiesForDifficulties(difficultyIds);
        java.util.Map<UUID, MapDifficultyStatisticsResponse> stats = statisticsService
                .findActiveForDifficulties(difficultyIds);

        return maps.map(map -> {
            List<MapDifficultyResponse> difficulties = byMap.getOrDefault(map.getId(), List.of()).stream()
                    .map(d -> toDifficultyResponse(d, complexities.get(d.getId()), stats.get(d.getId()), null))
                    .toList();
            return toMapResponse(map, difficulties);
        });
    }

    private Pageable resolveMapSort(Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        Sort resolved = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            String prop = order.getProperty();
            if (isTextSortField(prop)) {
                resolved = resolved.and(JpaSort.unsafe(order.getDirection(),
                        "LOWER(" + prop + ")"));
            } else {
                resolved = resolved.and(Sort.by(order));
            }
        }
        boolean hasId = pageable.getSort().stream().anyMatch(o -> "id".equals(o.getProperty()));
        if (!hasId) {
            resolved = resolved.and(Sort.by(Sort.Order.asc("id")));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
    }

    private static final java.util.Map<String, String> JPQL_SORT_MAPPING = java.util.Map.of(
            "complexity", "c.complexity",
            "songName", "d.map.songName",
            "songAuthor", "d.map.songAuthor",
            "mapAuthor", "d.map.mapAuthor",
            "totalScores", "mds.totalScores");

    private static boolean isTextSortField(String property) {
        return "songName".equals(property) || "songAuthor".equals(property) || "mapAuthor".equals(property);
    }

    private static final String RANK_RATING_SUBQUERY = "((SELECT COUNT(v) FROM StaffMapVote v WHERE v.mapDifficulty = d"
            + " AND v.type = 'rank' AND v.vote = 'upvote' AND v.active = true)"
            + " - (SELECT COUNT(v2) FROM StaffMapVote v2 WHERE v2.mapDifficulty = d"
            + " AND v2.type = 'rank' AND v2.vote = 'downvote' AND v2.active = true))";

    private Pageable resolveDifficultySort(Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        Sort resolved = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            if ("rating".equals(order.getProperty())) {
                resolved = resolved.and(JpaSort.unsafe(order.getDirection(), RANK_RATING_SUBQUERY));
            } else {
                String mapped = JPQL_SORT_MAPPING.get(order.getProperty());
                if (mapped != null) {
                    resolved = resolved
                            .and(JpaSort.unsafe(Sort.Direction.ASC,
                                    "(CASE WHEN " + mapped + " IS NULL THEN 1 ELSE 0 END)"))
                            .and(JpaSort.unsafe(order.getDirection(),
                                    isTextSortField(order.getProperty())
                                            ? "LOWER(" + mapped + ")"
                                            : mapped));
                } else {
                    resolved = resolved.and(Sort.by(
                            new Sort.Order(order.getDirection(), order.getProperty(),
                                    Sort.NullHandling.NULLS_LAST)));
                }
            }
        }
        boolean hasId = pageable.getSort().stream().anyMatch(o -> "id".equals(o.getProperty()));
        if (!hasId) {
            resolved = resolved.and(Sort.by(Sort.Order.asc("id")));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
    }

    public Page<MapDifficultyResponse> findDifficulties(UUID categoryId, MapDifficultyStatus status,
            BigDecimal complexityMin, BigDecimal complexityMax, String search, Long excludeUserId,
            Pageable pageable) {
        boolean hasSearch = search != null && !search.isBlank();
        Pageable effective = resolveDifficultySort(pageable);
        Page<MapDifficulty> difficulties = hasSearch
                ? mapDifficultyRepository.findWithComplexityFiltersWithSearch(
                        categoryId, status, complexityMin, complexityMax, excludeUserId, search.trim(), effective)
                : mapDifficultyRepository.findWithComplexityFilters(
                        categoryId, status, complexityMin, complexityMax, excludeUserId, effective);

        if (difficulties.isEmpty())
            return difficulties.map(d -> toDifficultyResponse(d, null, null, null));

        List<UUID> ids = difficulties.getContent().stream().map(MapDifficulty::getId).toList();
        java.util.Map<UUID, BigDecimal> complexities = complexityService.findActiveComplexitiesForDifficulties(ids);
        java.util.Map<UUID, MapDifficultyStatisticsResponse> stats = statisticsService.findActiveForDifficulties(ids);
        java.util.Map<UUID, StaffInfo> staffInfo = loadStaffInfo(difficulties.getContent());
        java.util.Map<UUID, VoteSummary> voteSummaries = loadVoteSummaries(ids);

        return difficulties.map(d -> toDifficultyResponse(d, complexities.get(d.getId()), stats.get(d.getId()),
                staffInfo.get(d.getLastUpdatedBy()), staffInfo.get(d.getCreatedBy()),
                voteSummaries.getOrDefault(d.getId(), EMPTY_SUMMARY)));
    }

    public MapResponse findById(UUID mapId) {
        Map map = mapRepository.findByIdAndActiveTrue(mapId)
                .orElseThrow(() -> new ResourceNotFoundException("Map", mapId));
        return toResponseWithAllDifficulties(map);
    }

    public List<MapDifficultyResponse> findDifficultiesByMapId(UUID mapId) {
        if (!mapRepository.existsById(mapId)) {
            throw new ResourceNotFoundException("Map", mapId);
        }
        List<MapDifficulty> difficulties = mapDifficultyRepository.findByMapIdAndActiveTrue(mapId);
        return enrichDifficulties(difficulties);
    }

    public MapResponse findBySongHash(String songHash, Difficulty difficulty) {
        Map map = mapRepository.findBySongHashAndActiveTrue(songHash.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Map", songHash));
        List<MapDifficulty> difficulties = mapDifficultyRepository.findByMapIdAndActiveTrue(map.getId());
        if (difficulty != null) {
            difficulties = difficulties.stream()
                    .filter(d -> d.getDifficulty() == difficulty)
                    .toList();
        }
        return toMapResponse(map, enrichDifficulties(difficulties));
    }

    public MapResponse findByBeatsaverCode(String beatsaverCode, Difficulty difficulty) {
        Map map = mapRepository.findByBeatsaverCodeAndActiveTrue(beatsaverCode)
                .orElseThrow(() -> new ResourceNotFoundException("Map", beatsaverCode));
        List<MapDifficulty> difficulties = mapDifficultyRepository.findByMapIdAndActiveTrue(map.getId());
        if (difficulty != null) {
            difficulties = difficulties.stream()
                    .filter(d -> d.getDifficulty() == difficulty)
                    .toList();
        }
        return toMapResponse(map, enrichDifficulties(difficulties));
    }

    public UUID findDifficultyIdByLeaderboardId(String leaderboardId) {
        return mapDifficultyRepository.findByBlLeaderboardId(leaderboardId)
                .or(() -> mapDifficultyRepository.findBySsLeaderboardId(leaderboardId))
                .map(MapDifficulty::getId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", leaderboardId));
    }

    public List<MapComplexityHistoryResponse> getComplexityHistory(UUID mapId) {
        if (!mapRepository.existsById(mapId)) {
            throw new ResourceNotFoundException("Map", mapId);
        }
        return complexityService.getHistoryForMap(mapId);
    }

    public List<MapDifficultyResponse> getDeactivated() {
        List<MapDifficulty> deactivated = mapDifficultyRepository.findByActiveFalseOrderByUpdatedAtDesc();
        if (deactivated.isEmpty())
            return List.of();

        java.util.Map<UUID, StaffInfo> staffInfo = loadStaffInfo(deactivated);
        List<UUID> ids = deactivated.stream().map(MapDifficulty::getId).toList();
        java.util.Map<UUID, BigDecimal> complexities = complexityService.findActiveComplexitiesForDifficulties(ids);

        return deactivated.stream()
                .map(d -> toDifficultyResponse(d, complexities.get(d.getId()), null,
                        staffInfo.get(d.getLastUpdatedBy())))
                .toList();
    }

    @Cacheable(value = "rankedDifficulties")
    public List<RankedDifficultyResponse> findAllRankedDifficulties() {
        return mapDifficultyRepository.findAllRankedWithComplexity().stream()
                .map(row -> RankedDifficultyResponse.builder()
                        .songHash((String) row[0])
                        .difficulty((Difficulty) row[1])
                        .complexity((BigDecimal) row[2])
                        .build())
                .toList();
    }

    @CacheEvict(value = "rankedDifficulties", allEntries = true)
    public void evictRankedDifficultiesCache() {
        log.info("Evicted ranked difficulties cache");
    }

    @Transactional
    public MapDifficultyResponse importMapDifficulty(CreateMapDifficultyRequest request, UUID staffId) {
        return importMapDifficulty(request, staffId, MapDifficultyStatus.QUEUE);
    }

    @Transactional
    public MapDifficultyResponse importMapDifficulty(CreateMapDifficultyRequest request, UUID staffId,
            MapDifficultyStatus status) {
        checkLeaderboardIdConflict(request.getBlLeaderboardId(), request.getSsLeaderboardId());

        Map map = mapRepository.findBySongHashAndActiveTrue(request.getSongHash())
                .orElseGet(() -> createMap(request));

        MapDifficulty previousVersion = mapDifficultyRepository
                .findByMapIdAndDifficultyAndCharacteristicAndActiveTrue(
                        map.getId(), request.getDifficulty(), request.getCharacteristic())
                .orElse(null);

        if (previousVersion != null) {
            boolean sameLeaderboards = Objects.equals(previousVersion.getBlLeaderboardId(),
                    request.getBlLeaderboardId())
                    && Objects.equals(previousVersion.getSsLeaderboardId(), request.getSsLeaderboardId());
            if (sameLeaderboards) {
                throw new ConflictException(
                        "A difficulty with the same leaderboard IDs already exists for this map");
            }
            if (previousVersion.getStatus() != MapDifficultyStatus.RANKED) {
                previousVersion.setActive(false);
                previousVersion.setLastUpdatedBy(staffId);
                mapDifficultyRepository.save(previousVersion);
            }
        }

        Category category = categoryRepository.findByIdAndActiveTrue(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));

        Batch batch = request.getBatchId() != null
                ? batchRepository.findById(request.getBatchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Batch", request.getBatchId()))
                : null;

        Instant rankedAt = null;
        if (status == MapDifficultyStatus.RANKED) {
            rankedAt = request.getRankedAt() != null ? request.getRankedAt() : Instant.now();
        }

        MapDifficulty difficulty = mapDifficultyRepository.save(MapDifficulty.builder()
                .map(map)
                .category(category)
                .difficulty(request.getDifficulty())
                .characteristic(request.getCharacteristic())
                .ssLeaderboardId(request.getSsLeaderboardId())
                .blLeaderboardId(request.getBlLeaderboardId())
                .maxScore(request.getMaxScore())
                .previousVersion(previousVersion)
                .status(status)
                .rankedAt(rankedAt)
                .batch(batch)
                .createdBy(staffId)
                .active(true)
                .build());

        if (status == MapDifficultyStatus.RANKED) {
            scoreIngestionService.refreshRankedLeaderboardIds();
        }

        return toDifficultyResponse(difficulty, null, null, null);
    }

    @Transactional
    public MapDifficultyResponse updateStatus(UUID difficultyId, UpdateMapStatusRequest request) {
        return updateStatus(difficultyId, request, null);
    }

    @Transactional
    public MapDifficultyResponse updateStatus(UUID difficultyId, UpdateMapStatusRequest request, UUID staffId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));

        MapDifficultyStatus oldStatus = difficulty.getStatus();
        if (request.getStatus() == MapDifficultyStatus.RANKED
                && complexityService.findActiveComplexity(difficultyId).isEmpty()) {
            throw new ValidationException("Cannot rank a difficulty without an active complexity");
        }
        difficulty.setStatus(request.getStatus());
        difficulty.setRankedAt(request.getStatus() == MapDifficultyStatus.RANKED ? Instant.now() : null);
        difficulty.setLastUpdatedBy(staffId);
        mapDifficultyRepository.save(difficulty);

        if (oldStatus != request.getStatus()
                && (oldStatus == MapDifficultyStatus.RANKED || request.getStatus() == MapDifficultyStatus.RANKED)) {
            scoreIngestionService.refreshRankedLeaderboardIds();
        }

        BigDecimal complexity = complexityService.findActiveComplexity(difficultyId).orElse(null);
        MapDifficultyStatisticsResponse stats = statisticsService.findActive(difficultyId).orElse(null);
        StaffInfo info = resolveStaffInfo(staffId);
        return toDifficultyResponse(difficulty, complexity, stats, info);
    }

    @Transactional
    public MapDifficultyResponse updateComplexity(UUID difficultyId, UpdateMapComplexityRequest request,
            Long staffUserId) {
        return updateComplexity(difficultyId, request, staffUserId, null);
    }

    @Transactional
    public MapDifficultyResponse updateComplexity(UUID difficultyId, UpdateMapComplexityRequest request,
            Long staffUserId, UUID staffId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));

        difficulty.setLastUpdatedBy(staffId);
        mapDifficultyRepository.save(difficulty);

        BigDecimal complexity = complexityService.setComplexity(
                difficulty, request.getComplexity(), request.getReason(), staffUserId);
        MapDifficultyStatisticsResponse stats = statisticsService.findActive(difficultyId).orElse(null);
        StaffInfo info = resolveStaffInfo(staffId);
        return toDifficultyResponse(difficulty, complexity, stats, info);
    }

    @Transactional
    public void deactivate(UUID difficultyId, UUID staffId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));
        difficulty.setActive(false);
        difficulty.setLastUpdatedBy(staffId);
        mapDifficultyRepository.save(difficulty);
    }

    public MapDifficultyResponse getDifficultyResponse(UUID difficultyId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));
        BigDecimal complexity = complexityService.findActiveComplexity(difficultyId).orElse(null);
        MapDifficultyStatisticsResponse stats = statisticsService.findActive(difficultyId).orElse(null);
        StaffInfo info = resolveStaffInfo(difficulty.getLastUpdatedBy());
        return toDifficultyResponse(difficulty, complexity, stats, info);
    }

    private void checkLeaderboardIdConflict(String blId, String ssId) {
        if (blId != null) {
            mapDifficultyRepository.findByBlLeaderboardId(blId)
                    .ifPresent(existing -> throwLeaderboardConflict("BeatLeader", blId, existing));
        }
        if (ssId != null) {
            mapDifficultyRepository.findBySsLeaderboardId(ssId)
                    .ifPresent(existing -> throwLeaderboardConflict("ScoreSaber", ssId, existing));
        }
    }

    private void throwLeaderboardConflict(String platform, String leaderboardId, MapDifficulty existing) {
        String activeState = existing.isActive() ? "active" : "deactivated (removed from ranking)";
        throw new ConflictException(String.format(
                "A map difficulty with %s leaderboard ID '%s' already exists (ID: %s, status: %s, %s)",
                platform, leaderboardId, existing.getId(), existing.getStatus(), activeState));
    }

    private Map createMap(CreateMapDifficultyRequest request) {
        return mapRepository.save(Map.builder()
                .songName(request.getSongName())
                .songSubName(request.getSongSubName())
                .songAuthor(request.getSongAuthor())
                .songHash(request.getSongHash())
                .mapAuthor(request.getMapAuthor())
                .beatsaverCode(request.getBeatsaverCode())
                .coverUrl(request.getCoverUrl())
                .active(true)
                .build());
    }

    private MapResponse toResponseWithAllDifficulties(Map map) {
        List<MapDifficulty> difficulties = mapDifficultyRepository.findByMapIdAndActiveTrue(map.getId());
        return toMapResponse(map, enrichDifficulties(difficulties));
    }

    private List<MapDifficultyResponse> enrichDifficulties(List<MapDifficulty> difficulties) {
        if (difficulties.isEmpty())
            return List.of();

        List<UUID> ids = difficulties.stream().map(MapDifficulty::getId).toList();
        java.util.Map<UUID, BigDecimal> complexities = complexityService.findActiveComplexitiesForDifficulties(ids);
        java.util.Map<UUID, MapDifficultyStatisticsResponse> stats = statisticsService.findActiveForDifficulties(ids);
        java.util.Map<UUID, StaffInfo> staffInfo = loadStaffInfo(difficulties);
        java.util.Map<UUID, VoteSummary> voteSummaries = loadVoteSummaries(ids);

        return difficulties.stream()
                .map(d -> toDifficultyResponse(d, complexities.get(d.getId()), stats.get(d.getId()),
                        staffInfo.get(d.getLastUpdatedBy()), staffInfo.get(d.getCreatedBy()),
                        voteSummaries.getOrDefault(d.getId(), EMPTY_SUMMARY)))
                .toList();
    }

    private java.util.Map<UUID, StaffInfo> loadStaffInfo(List<MapDifficulty> difficulties) {
        List<UUID> staffIds = difficulties.stream()
                .flatMap(d -> java.util.stream.Stream.of(d.getLastUpdatedBy(), d.getCreatedBy()))
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (staffIds.isEmpty())
            return new java.util.HashMap<>();

        return staffUserRepository.findAllByIdWithUser(staffIds).stream()
                .collect(Collectors.toMap(StaffUser::getId,
                        s -> new StaffInfo(s.getUsername(),
                                s.getUser() != null ? s.getUser().getAvatarUrl() : null)));
    }

    private java.util.Map<UUID, VoteSummary> loadVoteSummaries(List<UUID> difficultyIds) {
        if (difficultyIds.isEmpty())
            return java.util.Map.of();

        java.util.Map<UUID, int[]> rankCounts = new java.util.HashMap<>();
        for (Object[] row : voteRepository.countRankVotesByDifficultyIds(difficultyIds)) {
            UUID diffId = (UUID) row[0];
            VoteType voteType = (VoteType) row[1];
            int count = ((Long) row[2]).intValue();
            int[] pair = rankCounts.computeIfAbsent(diffId, k -> new int[] { 0, 0 });
            if (voteType == VoteType.UPVOTE)
                pair[0] = count;
            else if (voteType == VoteType.DOWNVOTE)
                pair[1] = count;
        }

        java.util.Map<UUID, int[]> critCounts = new java.util.HashMap<>();
        for (Object[] row : voteRepository.countCriteriaVotesByDifficultyIds(difficultyIds)) {
            UUID diffId = (UUID) row[0];
            VoteType voteType = (VoteType) row[1];
            int count = ((Long) row[2]).intValue();
            int[] pair = critCounts.computeIfAbsent(diffId, k -> new int[] { 0, 0 });
            if (voteType == VoteType.UPVOTE)
                pair[0] = count;
            else if (voteType == VoteType.DOWNVOTE)
                pair[1] = count;
        }

        java.util.Map<UUID, VoteType> headVotes = new java.util.HashMap<>();
        for (Object[] row : voteRepository.findHeadCriteriaVotesByDifficultyIds(difficultyIds)) {
            headVotes.put((UUID) row[0], (VoteType) row[1]);
        }

        java.util.Map<UUID, int[]> reweightCounts = new java.util.HashMap<>();
        java.util.Map<UUID, int[]> unrankCounts = new java.util.HashMap<>();
        for (Object[] row : voteRepository.countReweightAndUnrankVotesByDifficultyIds(difficultyIds)) {
            UUID diffId = (UUID) row[0];
            MapVoteAction type = (MapVoteAction) row[1];
            VoteType voteType = (VoteType) row[2];
            int count = ((Long) row[3]).intValue();
            java.util.Map<UUID, int[]> target = type == MapVoteAction.REWEIGHT ? reweightCounts : unrankCounts;
            int[] pair = target.computeIfAbsent(diffId, k -> new int[] { 0, 0 });
            if (voteType == VoteType.UPVOTE)
                pair[0] = count;
            else if (voteType == VoteType.DOWNVOTE)
                pair[1] = count;
        }

        java.util.Map<UUID, VoteSummary> result = new java.util.HashMap<>();
        for (UUID id : difficultyIds) {
            int[] rank = rankCounts.getOrDefault(id, new int[] { 0, 0 });
            int[] crit = critCounts.getOrDefault(id, new int[] { 0, 0 });
            int[] reweight = reweightCounts.getOrDefault(id, new int[] { 0, 0 });
            int[] unrank = unrankCounts.getOrDefault(id, new int[] { 0, 0 });
            result.put(id, new VoteSummary(rank[0], rank[1], crit[0], crit[1], headVotes.get(id),
                    reweight[0], reweight[1], unrank[0], unrank[1]));
        }
        return result;
    }

    private StaffInfo resolveStaffInfo(UUID staffId) {
        if (staffId == null)
            return null;
        return staffUserRepository.findAllByIdWithUser(List.of(staffId)).stream()
                .findFirst()
                .map(s -> new StaffInfo(s.getUsername(),
                        s.getUser() != null ? s.getUser().getAvatarUrl() : null))
                .orElse(null);
    }

    private MapResponse toMapResponse(Map map, List<MapDifficultyResponse> difficulties) {
        return MapResponse.builder()
                .id(map.getId())
                .songName(map.getSongName())
                .songSubName(map.getSongSubName())
                .songAuthor(map.getSongAuthor())
                .songHash(map.getSongHash())
                .mapAuthor(map.getMapAuthor())
                .beatsaverCode(map.getBeatsaverCode())
                .coverUrl(map.getCoverUrl())
                .difficulties(difficulties)
                .createdAt(map.getCreatedAt())
                .build();
    }

    private MapDifficultyResponse toDifficultyResponse(MapDifficulty d, BigDecimal complexity,
            MapDifficultyStatisticsResponse stats, StaffInfo lastUpdatedByInfo,
            StaffInfo createdByInfo, VoteSummary votes) {
        Map map = d.getMap();
        return MapDifficultyResponse.builder()
                .id(d.getId())
                .mapId(map.getId())
                .songName(map.getSongName())
                .songSubName(map.getSongSubName())
                .songAuthor(map.getSongAuthor())
                .mapAuthor(map.getMapAuthor())
                .coverUrl(map.getCoverUrl())
                .beatsaverCode(map.getBeatsaverCode())
                .categoryId(d.getCategory().getId())
                .difficulty(d.getDifficulty())
                .characteristic(d.getCharacteristic())
                .active(d.isActive())
                .status(d.getStatus())
                .criteriaStatus(d.getCriteriaStatus())
                .ssLeaderboardId(d.getSsLeaderboardId())
                .blLeaderboardId(d.getBlLeaderboardId())
                .maxScore(d.getMaxScore())
                .complexity(complexity)
                .rankedAt(d.getRankedAt())
                .previousVersionId(d.getPreviousVersion() != null ? d.getPreviousVersion().getId() : null)
                .createdAt(d.getCreatedAt())
                .createdBy(d.getCreatedBy())
                .createdByUsername(createdByInfo != null ? createdByInfo.username() : null)
                .createdByAvatarUrl(createdByInfo != null ? createdByInfo.avatarUrl() : null)
                .lastUpdatedBy(d.getLastUpdatedBy())
                .lastUpdatedByUsername(lastUpdatedByInfo != null ? lastUpdatedByInfo.username() : null)
                .rankUpvotes(d.getStatus() != MapDifficultyStatus.RANKED ? votes.rankUpvotes() : 0)
                .rankDownvotes(d.getStatus() != MapDifficultyStatus.RANKED ? votes.rankDownvotes() : 0)
                .criteriaUpvotes(d.getStatus() != MapDifficultyStatus.RANKED ? votes.criteriaUpvotes() : 0)
                .criteriaDownvotes(d.getStatus() != MapDifficultyStatus.RANKED ? votes.criteriaDownvotes() : 0)
                .headCriteriaVote(d.getStatus() != MapDifficultyStatus.RANKED ? votes.headCriteriaVote() : null)
                .reweightUpvotes(d.getStatus() == MapDifficultyStatus.RANKED ? votes.reweightUpvotes() : 0)
                .reweightDownvotes(d.getStatus() == MapDifficultyStatus.RANKED ? votes.reweightDownvotes() : 0)
                .unrankUpvotes(d.getStatus() == MapDifficultyStatus.RANKED ? votes.unrankUpvotes() : 0)
                .unrankDownvotes(d.getStatus() == MapDifficultyStatus.RANKED ? votes.unrankDownvotes() : 0)
                .statistics(stats)
                .build();
    }

    private MapDifficultyResponse toDifficultyResponse(MapDifficulty d, BigDecimal complexity,
            MapDifficultyStatisticsResponse stats, StaffInfo lastUpdatedByInfo) {
        return toDifficultyResponse(d, complexity, stats, lastUpdatedByInfo, null, EMPTY_SUMMARY);
    }
}
