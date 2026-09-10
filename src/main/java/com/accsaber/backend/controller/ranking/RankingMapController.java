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

    @Operation(summary = "List maps (staff)", description = "Full map list including complexity, submitter, and vote breakdowns")
    @GetMapping
    public ResponseEntity<Page<MapResponse>> listMaps(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "songName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(mapService.findAll(categoryId, status, search, pageable));
    }

    @Operation(summary = "Run the complexity script on one difficulty", description = "The complexity the script gives an active difficulty identified by song hash, difficulty and characteristic, priced the way import prices it: the chart line from the map's notes, plus the leaderboard term once the map is ranked and has a board. Any status works, so the queue and the qualified maps get the same number they would get on import. Null when the model could not read the map. The version says which build of the script priced it.")
    @GetMapping("/difficulties/ai-complexity")
    public ResponseEntity<ComplexityEstimateResponse> complexityEstimate(
            @RequestParam String songHash,
            @RequestParam Difficulty difficulty,
            @RequestParam String characteristic) {
        return ResponseEntity.ok(mapImportService.estimateForDifficulty(songHash, difficulty, characteristic));
    }

    @Operation(summary = "Preview a difficulty's leaderboard priced our way", description = "Fetches the difficulty's BeatLeader and ScoreSaber boards live, drops plays with banned modifiers, keeps one play per player with BeatLeader winning, and prices every play with our curve at the complexity the map carries today, or at the script's estimate when the map has none yet. Nothing is stored, so a queue map's scores stay invisible to milestones and statistics until it is ranked. Players we know come back with their AccSaber name and avatar, the rest with what the platform sent. The limit caps the rows and how deep the boards are read, 500 at most.")
    @GetMapping("/difficulties/{mapDifficultyId}/leaderboard-preview")
    public ResponseEntity<LeaderboardPreviewResponse> leaderboardPreview(
            @PathVariable UUID mapDifficultyId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(leaderboardPreviewService.preview(mapDifficultyId, limit));
    }

    @Operation(summary = "List difficulties (staff)", description = "Full difficulty list including complexity, submitter, and "
            + "all vote breakdowns. The status filter accepts multiple values (e.g. status=QUEUE,QUALIFIED). Pass batchId to "
            + "scope the list to a single batch (e.g. status=RANKED&batchId=... for the reweight queue of one batch). Pass "
            + "active=false to see difficulties that have been removed from the ranking system instead of the live ones, which "
            + "is usually paired with sort=updatedAt,desc to get the most recently removed first.")
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
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String characteristic) {
        return ResponseEntity.ok(mapService.findByBeatsaverCode(beatsaverCode, difficulty, characteristic));
    }

    @Operation(summary = "List difficulties for a map (staff)")
    @GetMapping("/{mapId}/difficulties")
    public ResponseEntity<List<MapDifficultyResponse>> listMapDifficulties(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findDifficultiesByMapId(mapId));
    }
}
