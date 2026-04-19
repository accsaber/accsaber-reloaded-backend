package com.accsaber.backend.controller.ranking;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.map.CastVoteRequest;
import com.accsaber.backend.model.dto.response.map.VoteListResponse;
import com.accsaber.backend.model.dto.response.map.VoteResponse;
import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.map.MapVotingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/ranking/maps/difficulties")
@RequiredArgsConstructor
@Tag(name = "Ranking - Map Votes")
public class RankingMapVoteController {

    private final MapVotingService mapVotingService;

    @Operation(summary = "Vote activity feed", description = "Paginated list of all active votes sorted by most recently updated")
    @GetMapping("/votes/activity")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<Page<VoteResponse>> getActivityFeed(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(mapVotingService.getActivityFeed(pageable));
    }

    @Operation(summary = "List votes on a map difficulty", description = "Returns all active votes plus informational threshold flags for reweight and unrank")
    @GetMapping("/{difficultyId}/votes")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<VoteListResponse> listVotes(
            @PathVariable UUID difficultyId,
            @RequestParam(defaultValue = "RANK") MapVoteAction type) {
        return ResponseEntity.ok(mapVotingService.getVotes(difficultyId, type));
    }

    @Operation(summary = "Cast or update a vote on a map difficulty")
    @PostMapping("/{difficultyId}/votes")
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
                request.getReason(),
                request.getCriteriaVote(),
                request.getCriteriaVoteOverride(),
                userDetails.getStaffUser().getRole()));
    }

    @Operation(summary = "Deactivate a vote", description = "Soft-deletes an active vote (ranking_head/admin only)")
    @DeleteMapping("/{difficultyId}/votes/{voteId}")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<Void> deactivateVote(
            @PathVariable UUID difficultyId,
            @PathVariable UUID voteId) {
        mapVotingService.deactivateVote(difficultyId, voteId);
        return ResponseEntity.noContent().build();
    }

}
