package com.accsaber.backend.controller.map;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.map.MapComplexityHistoryResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyStatisticsResponse;
import com.accsaber.backend.model.dto.response.map.MapResponse;
import com.accsaber.backend.model.dto.response.score.ScoreLeaderboardResponse;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.service.score.ScoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/maps")
@RequiredArgsConstructor
@Tag(name = "Maps")
public class MapController {

    private final MapService mapService;
    private final ScoreService scoreService;
    private final MapDifficultyStatisticsService statisticsService;

    @Operation(summary = "List maps", description = "Paginated map list, optionally filtered by category and/or status")
    @GetMapping
    public ResponseEntity<Page<MapResponse>> listMaps(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @PageableDefault(size = 20, sort = "songName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(mapService.findAll(categoryId, status, pageable));
    }

    @Operation(summary = "List difficulties", description = "Paginated difficulty list with map metadata, filterable by category, status, and complexity range. "
            + "Supports sorting by ANY difficulty field")
    @GetMapping("/difficulties")
    public ResponseEntity<Page<MapDifficultyResponse>> listDifficulties(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @RequestParam(required = false) BigDecimal complexityMin,
            @RequestParam(required = false) BigDecimal complexityMax,
            @PageableDefault(size = 20, sort = "rankedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity
                .ok(mapService.findDifficulties(categoryId, status, complexityMin, complexityMax, pageable));
    }

    @Operation(summary = "Get map by ID", description = "Returns a map with all its active difficulties, current complexities, and statistics")
    @GetMapping("/{mapId}")
    public ResponseEntity<MapResponse> getMap(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findById(mapId));
    }

    @Operation(summary = "List difficulties for a map")
    @GetMapping("/{mapId}/difficulties")
    public ResponseEntity<List<MapDifficultyResponse>> listMapDifficulties(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findDifficultiesByMapId(mapId));
    }

    @Operation(summary = "Difficulty leaderboard", description = "Paginated scores with player info for a specific difficulty, sorted by score descending. Optionally filter by country code (e.g. ES, GB)")
    @GetMapping("/difficulties/{difficultyId}/scores")
    public ResponseEntity<Page<ScoreLeaderboardResponse>> getDifficultyLeaderboard(
            @PathVariable UUID difficultyId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20, sort = "score", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(scoreService.findLeaderboardByMapDifficulty(difficultyId, country, pageable));
    }

    @Operation(summary = "Current statistics for a difficulty", description = "Returns the active aggregate statistics (maxAp, minAp, averageAp, totalScores) for a difficulty")
    @GetMapping("/difficulties/{difficultyId}/statistics")
    public ResponseEntity<MapDifficultyStatisticsResponse> getDifficultyStatistics(
            @PathVariable UUID difficultyId) {
        return statisticsService.findActive(difficultyId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Historic statistics for a difficulty", description = "Returns all versioned statistics for a difficulty over a time range, sorted by time ascending. "
            + "Units: h (hours), d (days), w (weeks), mo (months)")
    @GetMapping("/difficulties/{difficultyId}/statistics/historic")
    public ResponseEntity<List<MapDifficultyStatisticsResponse>> getDifficultyStatisticsHistoric(
            @PathVariable UUID difficultyId,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(statisticsService.findHistoric(difficultyId, amount, unit));
    }

    @Operation(summary = "Complexity version history for a map")
    @GetMapping("/{mapId}/complexity-history")
    public ResponseEntity<List<MapComplexityHistoryResponse>> getComplexityHistory(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.getComplexityHistory(mapId));
    }
}
