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
import com.accsaber.backend.model.dto.response.map.MapDifficultyStatisticsResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapResponse;
import com.accsaber.backend.model.dto.response.map.RankedDifficultyResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.score.ScoresAroundResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.clan.war.ClanWarRosterService;
import com.accsaber.backend.service.infra.CategoryService;
import com.accsaber.backend.service.map.MapDifficultyStatisticsService;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.service.player.UserRelationService;
import com.accsaber.backend.service.score.ScoreQueryService;

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
    private final ScoreQueryService scoreQueryService;
    private final MapDifficultyStatisticsService statisticsService;
    private final UserRelationService userRelationService;
    private final CategoryService categoryService;
    private final ClanWarRosterService clanWarRosterService;

    @Operation(summary = "List maps", description = "Category takes a UUID or code like true_acc. Search covers song "
            + "name, song author and mapper.")
    @GetMapping
    public ResponseEntity<Page<PublicMapResponse>> listMaps(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "songName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity
                .ok(mapService.findAllPublic(categoryService.resolveId(categoryId), status, search, pageable));
    }

    @Operation(summary = "List difficulties", description = "Status takes more than one value, like "
            + "status=QUEUE,QUALIFIED.")
    @GetMapping("/difficulties")
    public ResponseEntity<Page<PublicMapDifficultyResponse>> listDifficulties(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) List<MapDifficultyStatus> status,
            @RequestParam(required = false) Double complexityMin,
            @RequestParam(required = false) Double complexityMax,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "rankedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity
                .ok(mapService.findDifficultiesPublic(categoryService.resolveId(categoryId), status, complexityMin,
                        complexityMax, search, null, pageable));
    }

    @Operation(summary = "All ranked difficulties", description = "Use this to sync a local copy instead of paging "
            + "the difficulty list.")
    @GetMapping("/difficulties/all")
    public ResponseEntity<List<RankedDifficultyResponse>> getAllRankedDifficulties() {
        return ResponseEntity.ok(mapService.findAllRankedDifficulties());
    }

    @Operation(summary = "Get a difficulty", description = "Complexity only shows once ranked. Votes and criteria "
            + "only show before that.")
    @GetMapping("/difficulties/{difficultyId}")
    public ResponseEntity<PublicMapDifficultyResponse> getDifficulty(@PathVariable UUID difficultyId) {
        return ResponseEntity.ok(mapService.getDifficultyResponsePublic(difficultyId));
    }

    @Operation(summary = "Get a map")
    @GetMapping("/{mapId}")
    public ResponseEntity<PublicMapResponse> getMap(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findByIdPublic(mapId));
    }

    @Operation(summary = "Map by song hash", description = "Difficulty is EASY, NORMAL, HARD, EXPERT or "
            + "EXPERT_PLUS.")
    @GetMapping("/hash/{songHash}")
    public ResponseEntity<PublicMapResponse> getMapBySongHash(
            @PathVariable String songHash,
            @RequestParam(required = false) Difficulty difficulty) {
        return ResponseEntity.ok(mapService.findBySongHashPublic(songHash, difficulty));
    }

    @Operation(summary = "Map by BeatSaver code", description = "Difficulty and characteristic pick which version "
            + "of the map to return when several share the code; every difficulty of that version is included. "
            + "Characteristic is case insensitive.")
    @GetMapping("/by-code/{beatsaverCode}")
    public ResponseEntity<PublicMapResponse> getMapByBeatsaverCode(
            @PathVariable String beatsaverCode,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String characteristic) {
        return ResponseEntity.ok(mapService.findByBeatsaverCodePublic(beatsaverCode, difficulty, characteristic));
    }

    @Operation(summary = "Difficulties on a map")
    @GetMapping("/{mapId}/difficulties")
    public ResponseEntity<List<PublicMapDifficultyResponse>> listMapDifficulties(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.findDifficultiesByMapIdPublic(mapId));
    }

    @Operation(summary = "Leaderboard by platform ID", description = "Pass exactly one of the "
            + "BeatLeader or ScoreSaber leaderboard IDs. Same filters as the difficulty route.")
    @GetMapping("/difficulties/leaderboard/{leaderboardId}/scores")
    public ResponseEntity<Page<ScoreResponse>> getDifficultyScoresByLeaderboardId(
            @PathVariable String leaderboardId,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserRelationType relation,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20, sort = "score", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID difficultyId = mapService.findDifficultyIdByLeaderboardId(leaderboardId);
        return ResponseEntity.ok(scoreQueryService.findLeaderboardByMapDifficulty(difficultyId, country, search,
                userRelationService.resolveRelationFilter(relation, principal), pageable));
    }

    @Operation(summary = "Difficulty leaderboard", description = "Relation filter needs a player "
            + "token. warId keeps only that clan war's fighters and wins over relation. maxStreak115, playCount and "
            + "lastPlayedAt count every attempt.")
    @GetMapping("/difficulties/{difficultyId}/scores")
    public ResponseEntity<Page<ScoreResponse>> getDifficultyLeaderboard(
            @PathVariable UUID difficultyId,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserRelationType relation,
            @RequestParam(required = false) UUID warId,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20, sort = "score", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(scoreQueryService.findLeaderboardByMapDifficulty(difficultyId, country, search,
                warId != null ? clanWarRosterService.fighterIds(warId)
                        : userRelationService.resolveRelationFilter(relation, principal), pageable));
    }

    @Operation(summary = "Scores around a player", description = "Four above and five below by default. Looked up by "
            + "BeatLeader or ScoreSaber leaderboard ID.")
    @GetMapping("/difficulties/leaderboard/{leaderboardId}/scores-around/{userId}")
    public ResponseEntity<ScoresAroundResponse> getScoresAround(
            @PathVariable String leaderboardId,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "4") int above,
            @RequestParam(defaultValue = "5") int below) {
        UUID difficultyId = mapService.findDifficultyIdByLeaderboardId(leaderboardId);
        return ResponseEntity.ok(scoreQueryService.findScoresAround(difficultyId, userId, above, below));
    }

    @Operation(summary = "Difficulty stats now", description = "204 if nobody has scored on it yet.")
    @GetMapping("/difficulties/{difficultyId}/statistics")
    public ResponseEntity<MapDifficultyStatisticsResponse> getDifficultyStatistics(
            @PathVariable UUID difficultyId) {
        return statisticsService.findActive(difficultyId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Difficulty stats over time", description = "Unit is h, d, w or mo. Amount is how "
            + "many to go back.")
    @GetMapping("/difficulties/{difficultyId}/statistics/historic")
    public ResponseEntity<List<MapDifficultyStatisticsResponse>> getDifficultyStatisticsHistoric(
            @PathVariable UUID difficultyId,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(statisticsService.findHistoric(difficultyId, amount, unit));
    }

    @Operation(summary = "Map complexity history")
    @GetMapping("/{mapId}/complexity-history")
    public ResponseEntity<List<MapComplexityHistoryResponse>> getComplexityHistory(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.getComplexityHistory(mapId));
    }
}
