package com.accsaber.backend.controller.campaign;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.campaign.InviteCampaignCollaboratorRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignCollaboratorResponse;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.campaign.CampaignCollaboratorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns")
public class CampaignCollaboratorController {

    private final CampaignCollaboratorService collaboratorService;

    @Operation(summary = "Campaign collaborators")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{campaignId}/collaborators")
    public ResponseEntity<List<CampaignCollaboratorResponse>> listCollaborators(
            @PathVariable UUID campaignId,
            Authentication authentication) {
        return ResponseEntity.ok(collaboratorService.listCollaborators(
                StaffPrincipals.viewerIdOf(authentication), campaignId,
                StaffPrincipals.canViewCampaignDrafts(authentication)));
    }

    @Operation(summary = "Invite a collaborator", description = "They have to accept before they can edit.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/collaborators")
    public ResponseEntity<CampaignCollaboratorResponse> inviteCollaborator(
            @PathVariable UUID campaignId,
            @Valid @RequestBody InviteCampaignCollaboratorRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                collaboratorService.invite(principal.getUserId(), campaignId, request.getUserId()));
    }

    @Operation(summary = "Answer an invite", description = "accept=true to join, accept=false to decline.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}/collaborators/me")
    public ResponseEntity<CampaignCollaboratorResponse> respondToInvite(
            @PathVariable UUID campaignId,
            @RequestParam boolean accept,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(collaboratorService.respond(principal.getUserId(), campaignId, accept));
    }

    @Operation(summary = "Remove a collaborator", description = "Owners can kick anyone. Collaborators can remove "
            + "themselves.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/collaborators/{userId}")
    public ResponseEntity<Void> removeCollaborator(
            @PathVariable UUID campaignId,
            @PathVariable Long userId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        collaboratorService.remove(principal.getUserId(), campaignId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Your collaborations", description = "Includes invites you have not answered yet.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/collaborations")
    public ResponseEntity<Page<CampaignCollaboratorResponse>> listMyCollaborations(
            @RequestParam(required = false) CampaignCollaboratorStatus status,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                collaboratorService.listMyCollaborations(principal.getUserId(), status, pageable));
    }
}
