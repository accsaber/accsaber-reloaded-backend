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
@Tag(name = "Admin User Duplicates")
public class AdminUserDuplicateController {

    private final DuplicateUserService duplicateUserService;

    @Operation(summary = "Detect potential duplicate users", description = "Finds users with same country and 15+ shared scored map difficulties. Primary is the user with more BeatLeader scores.")
    @GetMapping
    public ResponseEntity<List<DuplicateCandidateResponse>> detectDuplicates() {
        return ResponseEntity.ok(duplicateUserService.detectDuplicates());
    }

    @Operation(summary = "Auto-merge all detected duplicates", description = "Detects all duplicate candidates and merges them automatically. Runs async — returns 202 immediately.")
    @PostMapping("/merge-all")
    public ResponseEntity<Void> mergeAllDuplicates(
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        duplicateUserService.mergeAllDetectedDuplicates(userDetails.getStaffUser().getId());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "List all duplicate links")
    @GetMapping("/links")
    public ResponseEntity<List<DuplicateLinkResponse>> listLinks() {
        return ResponseEntity.ok(duplicateUserService.listAllLinks());
    }

    @Operation(summary = "Create a duplicate link without merging")
    @PostMapping("/links")
    public ResponseEntity<DuplicateLinkResponse> createLink(
            @Valid @RequestBody MergeUsersRequest request) {
        return ResponseEntity.status(201).body(duplicateUserService.createLink(
                request.getPrimaryUserId(), request.getSecondaryUserId(), request.getReason()));
    }

    @Operation(summary = "Delete an unmerged duplicate link")
    @DeleteMapping("/links/{linkId}")
    public ResponseEntity<Void> deleteLink(@PathVariable UUID linkId) {
        duplicateUserService.deleteUnmergedLink(linkId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Merge secondary user into primary user", description = "Reassigns unique scores from secondary to primary, deactivates secondary")
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
}
