package com.accsaber.backend.controller.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.user.MergeUsersRequest;
import com.accsaber.backend.model.dto.response.player.DuplicateCandidateResponse;
import com.accsaber.backend.model.dto.response.player.DuplicateLinkResponse;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.player.DuplicateUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/users/duplicates")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Players")
public class AdminUserDuplicateController {

    private final DuplicateUserService duplicateUserService;

    @Operation(summary = "Find likely duplicate accounts", description = "Pairs with 8 or more identical scores on "
            + "the same difficulty. Primary is whoever has more BeatLeader scores.")
    @GetMapping
    public ResponseEntity<List<DuplicateCandidateResponse>> detectDuplicates() {
        return ResponseEntity.ok(duplicateUserService.detectDuplicates());
    }

    @Operation(summary = "List duplicate links")
    @GetMapping("/links")
    public ResponseEntity<List<DuplicateLinkResponse>> listLinks() {
        return ResponseEntity.ok(duplicateUserService.listAllLinks());
    }

    @Operation(summary = "Link duplicates without merging")
    @PostMapping("/links")
    public ResponseEntity<DuplicateLinkResponse> createLink(
            @Valid @RequestBody MergeUsersRequest request) {
        return ResponseEntity.status(201).body(duplicateUserService.createLink(
                request.getPrimaryUserId(), request.getSecondaryUserId(), request.getReason()));
    }

    @Operation(summary = "Remove an unmerged link")
    @DeleteMapping("/links/{linkId}")
    public ResponseEntity<Void> deleteLink(@PathVariable UUID linkId) {
        duplicateUserService.deleteUnmergedLink(linkId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Merge secondary into primary", description = "Moves unique scores to the primary "
            + "and deactivates the secondary.")
    @PostMapping("/merge")
    public ResponseEntity<DuplicateLinkResponse> mergeUsers(
            @Valid @RequestBody MergeUsersRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(duplicateUserService.merge(
                request.getPrimaryUserId(),
                request.getSecondaryUserId(),
                userDetails.getStaffUser().getId(),
                request.getReason()));
    }

    @Operation(summary = "Merge every pending link", description = "Recalculation runs once after all "
            + "merges finish.")
    @PostMapping("/merge-all")
    public ResponseEntity<List<DuplicateLinkResponse>> mergeAll(
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(duplicateUserService.mergeAllUnmerged(
                userDetails.getStaffUser().getId()));
    }

    @Operation(summary = "Undo a merge", description = "Moves the scores back and "
            + "reactivates the secondary.")
    @PostMapping("/unmerge/{linkId}")
    public ResponseEntity<DuplicateLinkResponse> unmergeUsers(@PathVariable UUID linkId) {
        return ResponseEntity.ok(duplicateUserService.unmerge(linkId));
    }
}
