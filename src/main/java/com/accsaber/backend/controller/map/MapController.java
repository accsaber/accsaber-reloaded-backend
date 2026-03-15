package com.accsaber.backend.controller.map;

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
import com.accsaber.backend.model.dto.response.map.MapResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
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

    @Operation(summary = "List maps", description = "Paginated map list, optionally filtered by category and/or status")
    @GetMapping
    public ResponseEntity<Page<MapResponse>> listMaps(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @PageableDefault(size = 20, sort = "songName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(mapService.findAll(categoryId, status, pageable));
    }

    @Operation(summary = "Get map by ID", description = "Returns a map with all its active difficulties, current complexities, and statistics")
    @GetMapping("/{mapId}")
    public ResponseEntity<MapResponse> getMap(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findById(mapId));
    }

    @Operation(summary = "List difficulties for a map")
    @GetMapping("/{mapId}/difficulties")
    public ResponseEntity<List<MapDifficultyResponse>> listDifficulties(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findDifficultiesByMapId(mapId));
    }

    @Operation(summary = "Difficulty leaderboard", description = "Paginated scores for a specific difficulty, sorted by score descending")
    @GetMapping("/difficulties/{difficultyId}/scores")
    public ResponseEntity<Page<ScoreResponse>> getDifficultyLeaderboard(
            @PathVariable UUID difficultyId,
            @PageableDefault(size = 20, sort = "score", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(scoreService.findByMapDifficulty(difficultyId, pageable));
    }

    @Operation(summary = "Complexity version history for a map")
    @GetMapping("/{mapId}/complexity-history")
    public ResponseEntity<List<MapComplexityHistoryResponse>> getComplexityHistory(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.getComplexityHistory(mapId));
    }
}
