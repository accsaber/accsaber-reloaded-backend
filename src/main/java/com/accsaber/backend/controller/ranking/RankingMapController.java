package com.accsaber.backend.controller.ranking;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.MapResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.service.map.MapService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/ranking/maps")
@PreAuthorize("hasRole('RANKING')")
@RequiredArgsConstructor
@Tag(name = "Ranking - Map Read")
public class RankingMapController {

    private final MapService mapService;

    @Operation(summary = "List maps (staff)", description = "Full map list including complexity, submitter, and vote breakdowns")
    @GetMapping
    public ResponseEntity<Page<MapResponse>> listMaps(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "songName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(mapService.findAll(categoryId, status, search, pageable));
    }

    @Operation(summary = "List difficulties (staff)", description = "Full difficulty list including complexity, submitter, and all vote breakdowns")
    @GetMapping("/difficulties")
    public ResponseEntity<Page<MapDifficultyResponse>> listDifficulties(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @RequestParam(required = false) BigDecimal complexityMin,
            @RequestParam(required = false) BigDecimal complexityMax,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "rankedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(mapService.findDifficulties(categoryId, status, complexityMin, complexityMax, search,
                null, pageable));
    }

    @Operation(summary = "Get difficulty by ID (staff)")
    @GetMapping("/difficulties/{difficultyId}")
    public ResponseEntity<MapDifficultyResponse> getDifficulty(@PathVariable UUID difficultyId) {
        return ResponseEntity.ok(mapService.getDifficultyResponse(difficultyId));
    }

    @Operation(summary = "Get map by ID (staff)")
    @GetMapping("/{mapId}")
    public ResponseEntity<MapResponse> getMap(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findById(mapId));
    }

    @Operation(summary = "Get map by song hash (staff)")
    @GetMapping("/hash/{songHash}")
    public ResponseEntity<MapResponse> getMapBySongHash(
            @PathVariable String songHash,
            @RequestParam(required = false) Difficulty difficulty) {
        return ResponseEntity.ok(mapService.findBySongHash(songHash, difficulty));
    }

    @Operation(summary = "Get map by BeatSaver code (staff)")
    @GetMapping("/by-code/{beatsaverCode}")
    public ResponseEntity<MapResponse> getMapByBeatsaverCode(
            @PathVariable String beatsaverCode,
            @RequestParam(required = false) Difficulty difficulty) {
        return ResponseEntity.ok(mapService.findByBeatsaverCode(beatsaverCode, difficulty));
    }

    @Operation(summary = "List difficulties for a map (staff)")
    @GetMapping("/{mapId}/difficulties")
    public ResponseEntity<List<MapDifficultyResponse>> listMapDifficulties(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findDifficultiesByMapId(mapId));
    }
}
