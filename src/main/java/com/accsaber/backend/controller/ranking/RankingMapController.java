package com.accsaber.backend.controller.ranking;

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

import com.accsaber.backend.model.dto.response.map.ComplexityEstimateResponse;
import com.accsaber.backend.model.dto.response.map.LeaderboardPreviewResponse;
import com.accsaber.backend.service.map.LeaderboardPreviewService;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.MapResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.service.map.MapImportService;
import com.accsaber.backend.service.map.MapService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/ranking/maps")
@PreAuthorize("hasRole('RANKING')")
@RequiredArgsConstructor
@Tag(name = "Ranking")
public class RankingMapController {

    private final MapService mapService;
    private final MapImportService mapImportService;
    private final LeaderboardPreviewService leaderboardPreviewService;

    @Operation(summary = "List maps (staff)", description = "Has complexity, submitter and vote breakdowns.")
    @GetMapping
    public ResponseEntity<Page<MapResponse>> listMaps(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "songName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(mapService.findAll(categoryId, status, search, pageable));
    }

    @Operation(summary = "Score one difficulty with the script", description = "Looks up by song hash, "
            + "difficulty and characteristic. Works on any status. Null when the model could not read the map.")
    @GetMapping("/difficulties/ai-complexity")
    public ResponseEntity<ComplexityEstimateResponse> complexityEstimate(
            @RequestParam String songHash,
            @RequestParam Difficulty difficulty,
            @RequestParam String characteristic) {
        return ResponseEntity.ok(mapImportService.estimateForDifficulty(songHash, difficulty, characteristic));
    }

    @Operation(summary = "Preview a live leaderboard with our AP", description = "Pulls BeatLeader and "
            + "ScoreSaber live and prices plays at the current or script complexity. Nothing is stored. limit caps "
            + "rows, 500 max.")
    @GetMapping("/difficulties/{mapDifficultyId}/leaderboard-preview")
    public ResponseEntity<LeaderboardPreviewResponse> leaderboardPreview(
            @PathVariable UUID mapDifficultyId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(leaderboardPreviewService.preview(mapDifficultyId, limit));
    }

    @Operation(summary = "List difficulties (staff)", description = "status takes several values, like "
            + "status=QUEUE,QUALIFIED. batchId scopes to one batch. active=false shows removed difficulties.")
    @GetMapping("/difficulties")
    public ResponseEntity<Page<MapDifficultyResponse>> listDifficulties(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) List<MapDifficultyStatus> status,
            @RequestParam(required = false) Double complexityMin,
            @RequestParam(required = false) Double complexityMax,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean active,
            @PageableDefault(size = 20, sort = "rankedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(mapService.findDifficulties(categoryId, batchId, status, complexityMin, complexityMax,
                search, null, active, pageable));
    }

    @Operation(summary = "Get a difficulty (staff)")
    @GetMapping("/difficulties/{difficultyId}")
    public ResponseEntity<MapDifficultyResponse> getDifficulty(@PathVariable UUID difficultyId) {
        return ResponseEntity.ok(mapService.getDifficultyResponse(difficultyId));
    }

    @Operation(summary = "Get a map (staff)")
    @GetMapping("/{mapId}")
    public ResponseEntity<MapResponse> getMap(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findById(mapId));
    }

    @Operation(summary = "Find a map by song hash")
    @GetMapping("/hash/{songHash}")
    public ResponseEntity<MapResponse> getMapBySongHash(
            @PathVariable String songHash,
            @RequestParam(required = false) Difficulty difficulty) {
        return ResponseEntity.ok(mapService.findBySongHash(songHash, difficulty));
    }

    @Operation(summary = "Find a map by BeatSaver code")
    @GetMapping("/by-code/{beatsaverCode}")
    public ResponseEntity<MapResponse> getMapByBeatsaverCode(
            @PathVariable String beatsaverCode,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String characteristic) {
        return ResponseEntity.ok(mapService.findByBeatsaverCode(beatsaverCode, difficulty, characteristic));
    }

    @Operation(summary = "A map's difficulties (staff)")
    @GetMapping("/{mapId}/difficulties")
    public ResponseEntity<List<MapDifficultyResponse>> listMapDifficulties(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findDifficultiesByMapId(mapId));
    }
}
