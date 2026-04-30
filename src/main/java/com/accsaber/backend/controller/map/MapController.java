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
import com.accsaber.backend.model.dto.response.map.MapDifficultyStatisticsResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapResponse;
import com.accsaber.backend.model.dto.response.map.RankedDifficultyResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.score.ScoresAroundResponse;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.service.player.UserRelationService;
import com.accsaber.backend.service.score.ScoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/v1/maps")
@RequiredArgsConstructor
@Tag(name = "Maps")
public class MapController {

    private final MapService mapService;
    private final ScoreService scoreService;
    private final MapDifficultyStatisticsService statisticsService;
    private final UserRelationService userRelationService;

    @Operation(summary = "List maps", description = "Paginated map list, optionally filtered by category, status, and/or search (matches song name, song author, or mapper)")
    @GetMapping
    public ResponseEntity<Page<PublicMapResponse>> listMaps(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "songName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(mapService.findAllPublic(categoryId, status, search, pageable));
    }

    @Operation(summary = "List difficulties", description = "Paginated difficulty list with map metadata, filterable by category, status, complexity range, and/or search (matches song name, song author, or mapper)")
    @GetMapping("/difficulties")
    public ResponseEntity<Page<PublicMapDifficultyResponse>> listDifficulties(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @RequestParam(required = false) BigDecimal complexityMin,
            @RequestParam(required = false) BigDecimal complexityMax,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "rankedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity
                .ok(mapService.findDifficultiesPublic(categoryId, status, complexityMin, complexityMax, search, null,
                        pageable));
    }

    @Operation(summary = "All ranked difficulties", description = "Returns a flat list of all ranked difficulties with song hash, difficulty level, and current complexity. Cached until ranked difficulties change.")
    @GetMapping("/difficulties/all")
    public ResponseEntity<List<RankedDifficultyResponse>> getAllRankedDifficulties() {
        return ResponseEntity.ok(mapService.findAllRankedDifficulties());
    }

    @Operation(summary = "Get difficulty by ID", description = "Returns a single map difficulty with public metadata; complexity is only included for RANKED difficulties, vote counts and criteria status only for non-RANKED")
    @GetMapping("/difficulties/{difficultyId}")
    public ResponseEntity<PublicMapDifficultyResponse> getDifficulty(@PathVariable UUID difficultyId) {
        return ResponseEntity.ok(mapService.getDifficultyResponsePublic(difficultyId));
    }

    @Operation(summary = "Get map by ID", description = "Returns a map with all its active difficulties")
    @GetMapping("/{mapId}")
    public ResponseEntity<PublicMapResponse> getMap(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findByIdPublic(mapId));
    }

    @Operation(summary = "Get map by song hash", description = "Returns a map by its song hash with active difficulties, optionally filtered by difficulty level (EASY, NORMAL, HARD, EXPERT, EXPERT_PLUS)")
    @GetMapping("/hash/{songHash}")
    public ResponseEntity<PublicMapResponse> getMapBySongHash(
            @PathVariable String songHash,
            @RequestParam(required = false) Difficulty difficulty) {
        return ResponseEntity.ok(mapService.findBySongHashPublic(songHash, difficulty));
    }

    @Operation(summary = "Get map by BeatSaver code", description = "Returns a map by its BeatSaver code with active difficulties, optionally filtered by difficulty level (EASY, NORMAL, HARD, EXPERT, EXPERT_PLUS)")
    @GetMapping("/by-code/{beatsaverCode}")
    public ResponseEntity<PublicMapResponse> getMapByBeatsaverCode(
            @PathVariable String beatsaverCode,
            @RequestParam(required = false) Difficulty difficulty) {
        return ResponseEntity.ok(mapService.findByBeatsaverCodePublic(beatsaverCode, difficulty));
    }

    @Operation(summary = "List difficulties for a map")
    @GetMapping("/{mapId}/difficulties")
    public ResponseEntity<List<PublicMapDifficultyResponse>> listMapDifficulties(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findDifficultiesByMapIdPublic(mapId));
    }

    @Operation(summary = "Difficulty scores by leaderboard ID", description = "Paginated scores for a difficulty looked up by BeatLeader or ScoreSaber leaderboard ID (provide exactly one). Optional relation filter restricts to the authenticated player's relations.")
    @GetMapping("/difficulties/leaderboard/{leaderboardId}/scores")
    public ResponseEntity<Page<ScoreResponse>> getDifficultyScoresByLeaderboardId(
            @PathVariable String leaderboardId,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserRelationType relation,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20, sort = "score", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID difficultyId = mapService.findDifficultyIdByLeaderboardId(leaderboardId);
        java.util.Collection<Long> filter = resolveRelationFilter(relation, principal);
        return ResponseEntity.ok(
                scoreService.findLeaderboardByMapDifficulty(difficultyId, country, search, filter, pageable));
    }

    @Operation(summary = "Difficulty leaderboard", description = "Paginated scores with player info for a specific difficulty, sorted by score descending. Optionally filter by country code (e.g. ES, GB), player name search, or relation type (auth required).")
    @GetMapping("/difficulties/{difficultyId}/scores")
    public ResponseEntity<Page<ScoreResponse>> getDifficultyLeaderboard(
            @PathVariable UUID difficultyId,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserRelationType relation,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20, sort = "score", direction = Sort.Direction.DESC) Pageable pageable) {
        java.util.Collection<Long> filter = resolveRelationFilter(relation, principal);
        return ResponseEntity.ok(
                scoreService.findLeaderboardByMapDifficulty(difficultyId, country, search, filter, pageable));
    }

    private java.util.Collection<Long> resolveRelationFilter(UserRelationType relation, PlayerUserDetails principal) {
        if (relation == null) {
            return null;
        }
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required to filter by relation");
        }
        return userRelationService.findRelationFilterUserIds(principal.getUserId(), relation);
    }

    @Operation(summary = "Scores around a player", description = "Returns scores above and below a player on a difficulty leaderboard. "
            + "Looked up by BeatLeader or ScoreSaber leaderboard ID. If fewer scores exist above/below, the remainder shifts to the other side.")
    @GetMapping("/difficulties/leaderboard/{leaderboardId}/scores-around/{userId}")
    public ResponseEntity<ScoresAroundResponse> getScoresAround(
            @PathVariable String leaderboardId,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "4") int above,
            @RequestParam(defaultValue = "5") int below) {
        UUID difficultyId = mapService.findDifficultyIdByLeaderboardId(leaderboardId);
        return ResponseEntity.ok(scoreService.findScoresAround(difficultyId, userId, above, below));
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
