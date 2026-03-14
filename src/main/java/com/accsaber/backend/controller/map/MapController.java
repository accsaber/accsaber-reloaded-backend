package com.accsaber.backend.controller.map;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.map.ApproveReweightRequest;
import com.accsaber.backend.model.dto.request.map.ApproveUnrankRequest;
import com.accsaber.backend.model.dto.request.map.BulkReweightRequest;
import com.accsaber.backend.model.dto.request.map.BulkUnrankRequest;
import com.accsaber.backend.model.dto.request.map.CastVoteRequest;
import com.accsaber.backend.model.dto.request.map.CreateMapDifficultyRequest;
import com.accsaber.backend.model.dto.request.map.CriteriaWebhookRequest;
import com.accsaber.backend.model.dto.request.map.ImportMapFromLeaderboardIdsRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapComplexityRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapStatusRequest;
import com.accsaber.backend.model.dto.response.map.MapComplexityHistoryResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.MapResponse;
import com.accsaber.backend.model.dto.response.map.VoteListResponse;
import com.accsaber.backend.model.dto.response.map.VoteResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.map.CriteriaService;
import com.accsaber.backend.service.map.MapImportService;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.service.map.MapVotingService;
import com.accsaber.backend.service.map.ReweightService;
import com.accsaber.backend.service.map.UnrankService;
import com.accsaber.backend.service.score.ScoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/maps")
@RequiredArgsConstructor
@Tag(name = "Maps")
public class MapController {

    private final MapService mapService;
    private final MapImportService mapImportService;
    private final MapVotingService mapVotingService;
    private final ScoreService scoreService;
    private final ReweightService reweightService;
    private final UnrankService unrankService;
    private final CriteriaService criteriaService;

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
    @GetMapping("/{mapId}/history")
    public ResponseEntity<List<MapComplexityHistoryResponse>> getHistory(@PathVariable UUID mapId) {
        return ResponseEntity.ok(mapService.getComplexityHistory(mapId));
    }

    @Operation(summary = "Import a map difficulty (Queue)", description = "Auto-fetches metadata from BeatLeader and BeatSaver, then creates the map difficulty in queue status")
    @PostMapping("/admin")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<MapDifficultyResponse> importMapDifficulty(
            @Valid @RequestBody ImportMapFromLeaderboardIdsRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        MapDifficultyResponse response = mapImportService.importByLeaderboardIds(
                request, userDetails.getStaffUser().getId(), MapDifficultyStatus.QUEUE);
        return ResponseEntity.created(URI.create("/v1/maps/difficulties/" + response.getId()))
                .body(response);
    }

    @Operation(summary = "Manual import a map difficulty", description = "Import with all fields provided manually (fallback when external APIs are unavailable)")
    @PostMapping("/admin/manual")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<MapDifficultyResponse> importMapDifficultyManual(
            @Valid @RequestBody CreateMapDifficultyRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        MapDifficultyResponse response = mapService.importMapDifficulty(request,
                userDetails.getStaffUser().getId());
        return ResponseEntity.created(URI.create("/v1/maps/difficulties/" + response.getId()))
                .body(response);
    }

    @Operation(summary = "List votes on a map difficulty", description = "Returns all active votes plus informational threshold flags for reweight and unrank")
    @GetMapping("/difficulties/{difficultyId}/votes")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<VoteListResponse> getVotes(@PathVariable UUID difficultyId) {
        return ResponseEntity.ok(mapVotingService.getVotes(difficultyId));
    }

    @Operation(summary = "Cast or update a vote on a map difficulty")
    @PostMapping("/difficulties/{difficultyId}/votes")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<VoteResponse> castVote(
            @PathVariable UUID difficultyId,
            @Valid @RequestBody CastVoteRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(mapVotingService.castVote(
                difficultyId,
                userDetails.getStaffUser().getId(),
                request.getVote(),
                request.getType(),
                request.getSuggestedComplexity(),
                request.getReason()));
    }

    @Operation(summary = "Deactivate a vote", description = "Soft-deletes an active vote (ranking_head/admin only)")
    @DeleteMapping("/difficulties/{difficultyId}/votes/{voteId}")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<Void> deactivateVote(
            @PathVariable UUID difficultyId,
            @PathVariable UUID voteId) {
        mapVotingService.deactivateVote(difficultyId, voteId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List deactivated map difficulties", description = "Returns all map difficulties that have been removed from the ranking system")
    @GetMapping("/difficulties/deactivated")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<List<MapDifficultyResponse>> getDeactivated() {
        return ResponseEntity.ok(mapService.getDeactivated());
    }

    @Operation(summary = "Update difficulty status", description = "Manually transition a map difficulty status (ranking_head/admin only)")
    @PatchMapping("/difficulties/{difficultyId}/status")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<MapDifficultyResponse> updateStatus(
            @PathVariable UUID difficultyId,
            @Valid @RequestBody UpdateMapStatusRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(mapService.updateStatus(difficultyId, request,
                userDetails.getStaffUser().getId()));
    }

    @Operation(summary = "Set difficulty complexity", description = "Versioned complexity update - deactivates current and inserts new version")
    @PostMapping("/difficulties/{difficultyId}/complexity")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<MapDifficultyResponse> updateComplexity(
            @PathVariable UUID difficultyId,
            @Valid @RequestBody UpdateMapComplexityRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(mapService.updateComplexity(difficultyId, request,
                getLinkedUserId(userDetails), userDetails.getStaffUser().getId()));
    }

    @Operation(summary = "Deactivate a map difficulty", description = "Soft-removes a map difficulty from the ranking system (ranking_head/admin only)")
    @PatchMapping("/difficulties/{difficultyId}/deactivate")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID difficultyId,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        mapService.deactivate(difficultyId, userDetails.getStaffUser().getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Approve and apply a reweight", description = "Sets new complexity on a RANKED difficulty and recalculates scores asynchronously")
    @PostMapping("/difficulties/{difficultyId}/reweight")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<MapDifficultyResponse> reweight(
            @PathVariable UUID difficultyId,
            @Valid @RequestBody ApproveReweightRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(reweightService.reweight(difficultyId, request.getComplexity(),
                request.getReason(), getLinkedUserId(userDetails), userDetails.getStaffUser().getId()));
    }

    @Operation(summary = "Bulk reweight", description = "Apply reweights to multiple RANKED difficulties in one request")
    @PostMapping("/difficulties/bulk-reweight")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<List<MapDifficultyResponse>> bulkReweight(
            @Valid @RequestBody BulkReweightRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(reweightService.reweightBatch(request.getItems(),
                getLinkedUserId(userDetails), userDetails.getStaffUser().getId()));
    }

    @Operation(summary = "Approve and apply an unrank", description = "Moves a RANKED difficulty back to QUEUE status")
    @PostMapping("/difficulties/{difficultyId}/unrank")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<MapDifficultyResponse> unrank(
            @PathVariable UUID difficultyId,
            @Valid @RequestBody ApproveUnrankRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(unrankService.unrank(difficultyId, request.getReason(),
                userDetails.getStaffUser().getId()));
    }

    @Operation(summary = "Bulk unrank", description = "Move multiple RANKED difficulties back to QUEUE in one request")
    @PostMapping("/difficulties/bulk-unrank")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<List<MapDifficultyResponse>> bulkUnrank(
            @Valid @RequestBody BulkUnrankRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(unrankService.unrankBatch(request.getItems(),
                userDetails.getStaffUser().getId()));
    }

    @Operation(summary = "Criteria checker webhook", description = "External service endpoint to update criteria pass/fail status on a difficulty")
    @PostMapping("/difficulties/criteria")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<Void> updateCriteria(@Valid @RequestBody CriteriaWebhookRequest request) {
        criteriaService.updateCriteriaStatus(request.getMapDifficultyId(), request.getStatus());
        return ResponseEntity.noContent().build();
    }

    private Long getLinkedUserId(StaffUserDetails userDetails) {
        return userDetails.getStaffUser().getUser() != null
                ? userDetails.getStaffUser().getUser().getId()
                : null;
    }
}
