package com.accsaber.backend.service.map;

import java.time.Instant;
import java.util.Collection;
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
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapResponse;
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
import com.accsaber.backend.service.playlist.PlaylistService;
import com.accsaber.backend.service.score.ScoreIngestionService;
import com.accsaber.backend.util.MapDifficultyMetrics;
import com.accsaber.backend.util.Rounding;

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
    private final com.accsaber.backend.repository.map.MapDifficultyComplexityEstimateRepository estimateRepository;
    private final ScoreIngestionService scoreIngestionService;
    private final com.accsaber.backend.service.score.CampaignScoreGate campaignScoreGate;
    private final PlaylistService playlistService;

    record StaffInfo(String username, String avatarUrl) {
    }

    record VoteSummary(int rankUpvotes, int rankDownvotes, int rankNeutrals, int criteriaUpvotes,
            int criteriaDownvotes,
            VoteType headCriteriaVote, int reweightUpvotes, int reweightDownvotes,
            int unrankUpvotes, int unrankDownvotes, Double averageVoteComplexity, int commentCount) {
    }

    private static final VoteSummary EMPTY_SUMMARY = new VoteSummary(0, 0, 0, 0, 0, null, 0, 0, 0, 0, null, 0);

    public Page<PublicMapResponse> findAllPublic(UUID categoryId, MapDifficultyStatus status, String search,
            Pageable pageable) {
        return findAll(categoryId, status, search, pageable).map(MapService::toPublicMapResponse);
    }

    public Page<PublicMapDifficultyResponse> findDifficultiesPublic(UUID categoryId,
            Collection<MapDifficultyStatus> statuses,
            Double complexityMin, Double complexityMax, String search, Long excludeUserId, Pageable pageable) {
        return findDifficulties(categoryId, null, statuses, complexityMin, complexityMax, search, excludeUserId,
                true, pageable)
                .map(MapService::toPublicDifficultyResponse);
    }

    public List<PublicMapDifficultyResponse> findDifficultiesWithUserScoreAbovePublic(
            Long userId, Double apMin, UUID categoryId) {
        List<MapDifficulty> difficulties = mapDifficultyRepository.findWithUserScoreAboveAp(
                userId, apMin, categoryId);

        if (difficulties.isEmpty())
            return List.of();

        List<UUID> ids = difficulties.stream().map(MapDifficulty::getId).toList();
        java.util.Map<UUID, Double> complexities = complexityService.findActiveComplexitiesForDifficulties(ids);
        java.util.Map<UUID, MapDifficultyStatisticsResponse> stats = statisticsService.findActiveForDifficulties(ids);
        java.util.Map<UUID, StaffInfo> staffInfo = loadStaffInfo(difficulties);
        java.util.Map<UUID, VoteSummary> voteSummaries = loadVoteSummaries(ids);

        return difficulties.stream()
                .map(d -> toPublicDifficultyResponse(toDifficultyResponse(d,
                        complexities.get(d.getId()), stats.get(d.getId()),
                        staffInfo.get(d.getLastUpdatedBy()), staffInfo.get(d.getCreatedBy()),
                        voteSummaries.getOrDefault(d.getId(), EMPTY_SUMMARY))))
                .toList();
    }

    public PublicMapResponse findByIdPublic(UUID mapId) {
        return toPublicMapResponse(findById(mapId));
    }

    public PublicMapResponse findBySongHashPublic(String songHash, Difficulty difficulty) {
        return toPublicMapResponse(findBySongHash(songHash, difficulty));
    }

    public PublicMapResponse findByBeatsaverCodePublic(String beatsaverCode, Difficulty difficulty,
            String characteristic) {
        return toPublicMapResponse(findByBeatsaverCode(beatsaverCode, difficulty, characteristic));
    }

    public List<PublicMapDifficultyResponse> findDifficultiesByMapIdPublic(UUID mapId) {
        return findDifficultiesByMapId(mapId).stream()
                .map(MapService::toPublicDifficultyResponse)
                .toList();
    }

    public PublicMapDifficultyResponse getDifficultyResponsePublic(UUID difficultyId) {
        return toPublicDifficultyResponse(getDifficultyResponse(difficultyId));
    }

    public java.util.Map<UUID, PublicMapDifficultyResponse> getDifficultyResponsesPublic(
            java.util.Collection<UUID> difficultyIds) {
        if (difficultyIds.isEmpty()) {
            return java.util.Map.of();
        }
        List<UUID> ids = difficultyIds.stream().distinct().toList();
        List<MapDifficulty> difficulties = mapDifficultyRepository.findAllByIdInAndActiveTrueWithMapAndCategory(ids);
        java.util.Map<UUID, Double> complexities = complexityService.findActiveComplexitiesForDifficulties(ids);
        java.util.Map<UUID, MapDifficultyStatisticsResponse> stats = statisticsService.findActiveForDifficulties(ids);
        java.util.Map<UUID, StaffInfo> staffInfo = loadStaffInfo(difficulties);
        java.util.Map<UUID, PublicMapDifficultyResponse> result = new java.util.HashMap<>();
        for (MapDifficulty d : difficulties) {
            MapDifficultyResponse full = toDifficultyResponse(d, complexities.get(d.getId()), stats.get(d.getId()),
                    staffInfo.get(d.getLastUpdatedBy()));
            result.put(d.getId(), toPublicDifficultyResponse(full));
        }
        return result;
    }

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
        java.util.Map<UUID, Double> complexities = complexityService
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

    private static final String COMMENT_COUNT_SUBQUERY = "(SELECT COUNT(v3) FROM StaffMapVote v3"
            + " WHERE v3.mapDifficulty = d AND v3.active = true"
            + " AND v3.reason IS NOT NULL AND TRIM(v3.reason) <> '')";

    private Pageable resolveDifficultySort(Pageable pageable) {
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        Sort resolved = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            if ("rating".equals(order.getProperty())) {
                resolved = resolved.and(JpaSort.unsafe(order.getDirection(), RANK_RATING_SUBQUERY));
            } else if ("commentCount".equals(order.getProperty())) {
                resolved = resolved.and(JpaSort.unsafe(order.getDirection(), COMMENT_COUNT_SUBQUERY));
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

    public Page<MapDifficultyResponse> findDifficulties(UUID categoryId, UUID batchId,
            Collection<MapDifficultyStatus> statuses,
            Double complexityMin, Double complexityMax, String search, Long excludeUserId,
            boolean active, Pageable pageable) {
        boolean hasSearch = search != null && !search.isBlank();
        Collection<MapDifficultyStatus> statusFilter = resolveStatusFilter(statuses, active);
        Pageable effective = resolveDifficultySort(pageable);
        Page<MapDifficulty> difficulties = hasSearch
                ? mapDifficultyRepository.findWithComplexityFiltersWithSearch(
                        categoryId, active, batchId, statusFilter, complexityMin, complexityMax, excludeUserId,
                        search.trim(), effective)
                : mapDifficultyRepository.findWithComplexityFilters(
                        categoryId, active, batchId, statusFilter, complexityMin, complexityMax, excludeUserId,
                        effective);

        if (difficulties.isEmpty())
            return difficulties.map(d -> toDifficultyResponse(d, null, null, null));

        List<UUID> ids = difficulties.getContent().stream().map(MapDifficulty::getId).toList();
        java.util.Map<UUID, Double> complexities = complexityService.findActiveComplexitiesForDifficulties(ids);
        java.util.Map<UUID, MapDifficultyStatisticsResponse> stats = statisticsService.findActiveForDifficulties(ids);
        java.util.Map<UUID, StaffInfo> staffInfo = loadStaffInfo(difficulties.getContent());
        java.util.Map<UUID, VoteSummary> voteSummaries = loadVoteSummaries(ids);
        java.util.Map<UUID, com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate> estimates = loadEstimates(ids);

        return difficulties.map(d -> toDifficultyResponse(d, complexities.get(d.getId()), stats.get(d.getId()),
                staffInfo.get(d.getLastUpdatedBy()), staffInfo.get(d.getCreatedBy()),
                voteSummaries.getOrDefault(d.getId(), EMPTY_SUMMARY), estimates.get(d.getId())));
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

    public MapResponse findByBeatsaverCode(String beatsaverCode, Difficulty difficulty, String characteristic) {
        String characteristicParam = characteristic == null || characteristic.isBlank() ? null : characteristic;
        Map map = null;
        if (difficulty != null || characteristicParam != null) {
            map = mapRepository.findActiveByBeatsaverCodeMatchingDifficulty(
                    beatsaverCode, difficulty, characteristicParam, PageRequest.of(0, 1))
                    .stream().findFirst().orElse(null);
        }
        if (map == null) {
            map = mapRepository.findActiveByBeatsaverCodeLatestFirst(beatsaverCode, PageRequest.of(0, 1)).stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Map", beatsaverCode));
        }
        List<MapDifficulty> difficulties = mapDifficultyRepository.findByMapIdAndActiveTrue(map.getId());
        if (difficulty != null) {
            difficulties = difficulties.stream()
                    .filter(d -> d.getDifficulty() == difficulty)
                    .toList();
        }
        if (characteristicParam != null) {
            difficulties = difficulties.stream()
                    .filter(d -> characteristicParam.equalsIgnoreCase(d.getCharacteristic()))
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

    private Collection<MapDifficultyStatus> resolveStatusFilter(Collection<MapDifficultyStatus> statuses,
            boolean active) {
        if (statuses != null && !statuses.isEmpty()) {
            return statuses;
        }
        return active ? null : List.of(MapDifficultyStatus.values());
    }

    @Cacheable(value = "rankedDifficulties")
    public List<RankedDifficultyResponse> findAllRankedDifficulties() {
        return mapDifficultyRepository.findAllRankedWithComplexity().stream()
                .map(row -> RankedDifficultyResponse.builder()
                        .id((UUID) row[0])
                        .songHash((String) row[1])
                        .songName((String) row[2])
                        .songSubName((String) row[3])
                        .difficulty((Difficulty) row[4])
                        .complexity((Double) row[5])
                        .categoryCode((String) row[6])
                        .ssLeaderboardId((String) row[7])
                        .blLeaderboardId((String) row[8])
                        .rankedAt((Instant) row[9])
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
            if (previousVersion.getStatus() == MapDifficultyStatus.CAMPAIGN) {
                throw new ConflictException(
                        "A campaign-imported difficulty already exists for this map; promote it instead of importing a new one");
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
                .metadata(request.getMetadata())
                .previousVersion(previousVersion)
                .status(status)
                .rankedAt(rankedAt)
                .batch(batch)
                .createdBy(staffId)
                .active(true)
                .build());

        if (status == MapDifficultyStatus.RANKED) {
            scoreIngestionService.refreshRankedLeaderboardIds();
            playlistService.evictAllPlaylists();
        } else {
            playlistService.evictAllUnrankedPlaylists();
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
        if (request.getStatus() == MapDifficultyStatus.CAMPAIGN) {
            throw new ValidationException("Campaign status can only be set through campaign map import");
        }
        if (oldStatus == MapDifficultyStatus.CAMPAIGN && difficulty.getCategory() == null) {
            throw new ValidationException(
                    "Assign a category before promoting a campaign-imported difficulty");
        }
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
        if (oldStatus != request.getStatus()) {
            campaignScoreGate.refresh();
        }

        Double complexity = complexityService.findActiveComplexity(difficultyId).orElse(null);
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

        Double complexity = complexityService.setComplexity(
                difficulty, request.getComplexity(), request.getReason(), staffUserId);
        MapDifficultyStatisticsResponse stats = statisticsService.findActive(difficultyId).orElse(null);
        StaffInfo info = resolveStaffInfo(staffId);
        return toDifficultyResponse(difficulty, complexity, stats, info);
    }

    @Transactional
    public MapDifficultyResponse updateCategory(UUID difficultyId, UUID categoryId, UUID staffId) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));
        if (difficulty.getStatus() == MapDifficultyStatus.RANKED) {
            throw new ValidationException("Cannot change category on a RANKED difficulty");
        }
        Category category = categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        difficulty.setCategory(category);
        difficulty.setLastUpdatedBy(staffId);
        mapDifficultyRepository.save(difficulty);

        Double complexity = complexityService.findActiveComplexity(difficultyId).orElse(null);
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
        Double complexity = complexityService.findActiveComplexity(difficultyId).orElse(null);
        MapDifficultyStatisticsResponse stats = statisticsService.findActive(difficultyId).orElse(null);
        StaffInfo info = resolveStaffInfo(difficulty.getLastUpdatedBy());
        Double avgComplexity = loadAvgReweightComplexity(List.of(difficultyId)).get(difficultyId);
        VoteSummary votes = new VoteSummary(0, 0, 0, 0, 0, null, 0, 0, 0, 0, avgComplexity, 0);
        return toDifficultyResponse(difficulty, complexity, stats, info, null, votes,
                estimateRepository.findByMapDifficultyId(difficultyId).orElse(null));
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
        java.util.Map<UUID, Double> complexities = complexityService.findActiveComplexitiesForDifficulties(ids);
        java.util.Map<UUID, MapDifficultyStatisticsResponse> stats = statisticsService.findActiveForDifficulties(ids);
        java.util.Map<UUID, StaffInfo> staffInfo = loadStaffInfo(difficulties);
        java.util.Map<UUID, VoteSummary> voteSummaries = loadVoteSummaries(ids);
        java.util.Map<UUID, com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate> estimates = loadEstimates(ids);

        return difficulties.stream()
                .map(d -> toDifficultyResponse(d, complexities.get(d.getId()), stats.get(d.getId()),
                        staffInfo.get(d.getLastUpdatedBy()), staffInfo.get(d.getCreatedBy()),
                        voteSummaries.getOrDefault(d.getId(), EMPTY_SUMMARY), estimates.get(d.getId())))
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
            int[] counts = rankCounts.computeIfAbsent(diffId, k -> new int[] { 0, 0, 0 });
            if (voteType == VoteType.UPVOTE)
                counts[0] = count;
            else if (voteType == VoteType.DOWNVOTE)
                counts[1] = count;
            else if (voteType == VoteType.NEUTRAL)
                counts[2] = count;
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

        java.util.Map<UUID, Double> avgComplexities = loadAvgReweightComplexity(difficultyIds);

        java.util.Map<UUID, Integer> commentCounts = new java.util.HashMap<>();
        for (Object[] row : voteRepository.countCommentsByDifficultyIds(difficultyIds)) {
            commentCounts.put((UUID) row[0], ((Long) row[1]).intValue());
        }

        java.util.Map<UUID, VoteSummary> result = new java.util.HashMap<>();
        for (UUID id : difficultyIds) {
            int[] rank = rankCounts.getOrDefault(id, new int[] { 0, 0, 0 });
            int[] crit = critCounts.getOrDefault(id, new int[] { 0, 0 });
            int[] reweight = reweightCounts.getOrDefault(id, new int[] { 0, 0 });
            int[] unrank = unrankCounts.getOrDefault(id, new int[] { 0, 0 });
            result.put(id, new VoteSummary(rank[0], rank[1], rank[2], crit[0], crit[1], headVotes.get(id),
                    reweight[0], reweight[1], unrank[0], unrank[1], avgComplexities.get(id),
                    commentCounts.getOrDefault(id, 0)));
        }
        return result;
    }

    private java.util.Map<UUID, Double> loadAvgReweightComplexity(List<UUID> difficultyIds) {
        java.util.Map<UUID, Double> result = new java.util.HashMap<>();
        if (difficultyIds.isEmpty())
            return result;
        for (Object[] row : voteRepository.sumSuggestedComplexityByDifficultyIds(difficultyIds)) {
            Double sum = (Double) row[1];
            long count = ((Number) row[2]).longValue();
            if (sum != null && count > 0)
                result.put((UUID) row[0], Rounding.round(sum / (double) (count), 6));
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
                .cdnCoverUrl(map.getCdnCoverUrl())
                .difficulties(difficulties)
                .createdAt(map.getCreatedAt())
                .build();
    }

    private MapDifficultyResponse toDifficultyResponse(MapDifficulty d, Double complexity,
            MapDifficultyStatisticsResponse stats, StaffInfo lastUpdatedByInfo,
            StaffInfo createdByInfo, VoteSummary votes,
            com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate estimate) {
        Map map = d.getMap();
        return MapDifficultyResponse.builder()
                .id(d.getId())
                .mapId(map.getId())
                .songName(map.getSongName())
                .songSubName(map.getSongSubName())
                .songAuthor(map.getSongAuthor())
                .mapAuthor(map.getMapAuthor())
                .coverUrl(map.getCoverUrl())
                .cdnCoverUrl(map.getCdnCoverUrl())
                .beatsaverCode(map.getBeatsaverCode())
                .categoryId(d.getCategory() != null ? d.getCategory().getId() : null)
                .difficulty(d.getDifficulty())
                .characteristic(d.getCharacteristic())
                .active(d.isActive())
                .status(d.getStatus())
                .criteriaStatus(d.getCriteriaStatus())
                .autoCriteriaStatus(d.getAutoCriteriaStatus())
                .ssLeaderboardId(d.getSsLeaderboardId())
                .blLeaderboardId(d.getBlLeaderboardId())
                .maxScore(d.getMaxScore())
                .metadata(d.getMetadata())
                .nps(MapDifficultyMetrics.nps(d.getMetadata()))
                .maxCombo(MapDifficultyMetrics.maxCombo(d.getMetadata()))
                .complexity(complexity)
                .scriptComplexity(estimate == null ? null : estimate.getComplexity())
                .scriptVersion(estimate == null ? null : estimate.getVersion())
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
                .rankNeutrals(d.getStatus() != MapDifficultyStatus.RANKED ? votes.rankNeutrals() : 0)
                .criteriaUpvotes(d.getStatus() != MapDifficultyStatus.RANKED ? votes.criteriaUpvotes() : 0)
                .criteriaDownvotes(d.getStatus() != MapDifficultyStatus.RANKED ? votes.criteriaDownvotes() : 0)
                .headCriteriaVote(d.getStatus() != MapDifficultyStatus.RANKED ? votes.headCriteriaVote() : null)
                .reweightUpvotes(d.getStatus() == MapDifficultyStatus.RANKED ? votes.reweightUpvotes() : 0)
                .reweightDownvotes(d.getStatus() == MapDifficultyStatus.RANKED ? votes.reweightDownvotes() : 0)
                .unrankUpvotes(d.getStatus() == MapDifficultyStatus.RANKED ? votes.unrankUpvotes() : 0)
                .unrankDownvotes(d.getStatus() == MapDifficultyStatus.RANKED ? votes.unrankDownvotes() : 0)
                .averageVoteComplexity(
                        d.getStatus() == MapDifficultyStatus.RANKED ? votes.averageVoteComplexity() : null)
                .commentCount(votes.commentCount())
                .statistics(stats)
                .build();
    }

    private java.util.Map<UUID, com.accsaber.backend.model.entity.map.MapDifficultyComplexityEstimate> loadEstimates(
            List<UUID> ids) {
        return estimateRepository.findAllByDifficultyIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.getMapDifficulty().getId(), e -> e));
    }

    private MapDifficultyResponse toDifficultyResponse(MapDifficulty d, Double complexity,
            MapDifficultyStatisticsResponse stats, StaffInfo lastUpdatedByInfo) {
        return toDifficultyResponse(d, complexity, stats, lastUpdatedByInfo, null, EMPTY_SUMMARY, null);
    }

    private MapDifficultyResponse toDifficultyResponse(MapDifficulty d, Double complexity,
            MapDifficultyStatisticsResponse stats, StaffInfo lastUpdatedByInfo,
            StaffInfo createdByInfo, VoteSummary votes) {
        return toDifficultyResponse(d, complexity, stats, lastUpdatedByInfo, createdByInfo, votes, null);
    }

    public static PublicMapResponse toPublicMapResponse(MapResponse map) {
        return PublicMapResponse.builder()
                .id(map.getId())
                .songName(map.getSongName())
                .songSubName(map.getSongSubName())
                .songAuthor(map.getSongAuthor())
                .songHash(map.getSongHash())
                .mapAuthor(map.getMapAuthor())
                .beatsaverCode(map.getBeatsaverCode())
                .coverUrl(map.getCoverUrl())
                .cdnCoverUrl(map.getCdnCoverUrl())
                .difficulties(map.getDifficulties().stream()
                        .map(MapService::toPublicDifficultyResponse)
                        .toList())
                .createdAt(map.getCreatedAt())
                .build();
    }

    public static PublicMapDifficultyResponse toPublicDifficultyResponse(MapDifficultyResponse d) {
        boolean ranked = d.getStatus() == MapDifficultyStatus.RANKED;
        return PublicMapDifficultyResponse.builder()
                .id(d.getId())
                .mapId(d.getMapId())
                .songName(d.getSongName())
                .songSubName(d.getSongSubName())
                .songAuthor(d.getSongAuthor())
                .mapAuthor(d.getMapAuthor())
                .coverUrl(d.getCoverUrl())
                .cdnCoverUrl(d.getCdnCoverUrl())
                .beatsaverCode(d.getBeatsaverCode())
                .categoryId(d.getCategoryId())
                .difficulty(d.getDifficulty())
                .characteristic(d.getCharacteristic())
                .status(d.getStatus())
                .ssLeaderboardId(d.getSsLeaderboardId())
                .blLeaderboardId(d.getBlLeaderboardId())
                .maxScore(d.getMaxScore())
                .metadata(d.getMetadata())
                .nps(d.getNps())
                .maxCombo(d.getMaxCombo())
                .rankedAt(d.getRankedAt())
                .createdAt(d.getCreatedAt())
                .complexity(ranked ? d.getComplexity() : null)
                .rankUpvotes(ranked ? null : d.getRankUpvotes())
                .rankDownvotes(ranked ? null : d.getRankDownvotes())
                .rankNeutrals(ranked ? null : d.getRankNeutrals())
                .criteriaStatus(ranked ? null : d.getCriteriaStatus())
                .autoCriteriaStatus(ranked ? null : d.getAutoCriteriaStatus())
                .statistics(d.getStatistics())
                .build();
    }
}
