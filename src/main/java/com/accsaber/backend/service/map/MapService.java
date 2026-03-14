package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.map.CreateMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapStatusRequest;
import com.accsaber.backend.model.dto.response.map.MapComplexityHistoryResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyStatisticsResponse;
import com.accsaber.backend.model.dto.response.map.MapResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapService {

    private final MapRepository mapRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final CategoryRepository categoryRepository;
    private final BatchRepository batchRepository;
    private final MapDifficultyComplexityService complexityService;
    private final MapDifficultyStatisticsService statisticsService;
    private final StaffUserRepository staffUserRepository;

    public Page<MapResponse> findAll(UUID categoryId, MapDifficultyStatus status, Pageable pageable) {
        Page<Map> maps = mapRepository.findByDifficultyFilters(categoryId, status, pageable);
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

        java.util.Map<UUID, String> staffUsernames = loadStaffUsernames(deactivated);
        List<UUID> ids = deactivated.stream().map(MapDifficulty::getId).toList();
        java.util.Map<UUID, BigDecimal> complexities = complexityService.findActiveComplexitiesForDifficulties(ids);

        return deactivated.stream()
                .map(d -> toDifficultyResponse(d, complexities.get(d.getId()), null,
                        staffUsernames.get(d.getLastUpdatedBy())))
                .toList();
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
                .active(true)
                .build());

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

        difficulty.setStatus(request.getStatus());
        difficulty.setRankedAt(request.getStatus() == MapDifficultyStatus.RANKED ? Instant.now() : null);
        difficulty.setLastUpdatedBy(staffId);
        mapDifficultyRepository.save(difficulty);

        BigDecimal complexity = complexityService.findActiveComplexity(difficultyId).orElse(null);
        MapDifficultyStatisticsResponse stats = statisticsService.findActive(difficultyId).orElse(null);
        String staffUsername = resolveStaffUsername(staffId);
        return toDifficultyResponse(difficulty, complexity, stats, staffUsername);
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
        String staffUsername = resolveStaffUsername(staffId);
        return toDifficultyResponse(difficulty, complexity, stats, staffUsername);
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
        String staffUsername = resolveStaffUsername(difficulty.getLastUpdatedBy());
        return toDifficultyResponse(difficulty, complexity, stats, staffUsername);
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
        java.util.Map<UUID, String> staffUsernames = loadStaffUsernames(difficulties);

        return difficulties.stream()
                .map(d -> toDifficultyResponse(d, complexities.get(d.getId()), stats.get(d.getId()),
                        staffUsernames.get(d.getLastUpdatedBy())))
                .toList();
    }

    private java.util.Map<UUID, String> loadStaffUsernames(List<MapDifficulty> difficulties) {
        List<UUID> staffIds = difficulties.stream()
                .map(MapDifficulty::getLastUpdatedBy)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (staffIds.isEmpty())
            return new java.util.HashMap<>();

        return staffUserRepository.findAllById(staffIds).stream()
                .collect(Collectors.toMap(StaffUser::getId, StaffUser::getUsername));
    }

    private String resolveStaffUsername(UUID staffId) {
        if (staffId == null)
            return null;
        return staffUserRepository.findById(staffId).map(StaffUser::getUsername).orElse(null);
    }

    private MapResponse toMapResponse(Map map, List<MapDifficultyResponse> difficulties) {
        return MapResponse.builder()
                .id(map.getId())
                .songName(map.getSongName())
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
            MapDifficultyStatisticsResponse stats, String lastUpdatedByUsername) {
        return MapDifficultyResponse.builder()
                .id(d.getId())
                .mapId(d.getMap().getId())
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
                .lastUpdatedBy(d.getLastUpdatedBy())
                .lastUpdatedByUsername(lastUpdatedByUsername)
                .statistics(stats)
                .build();
    }
}
