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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.campaign.InviteCampaignCollaboratorRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignCollaboratorResponse;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.staff.StaffRole;
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

    private static Long viewerId(Authentication authentication) {
        return authentication != null ? StaffPrincipals.linkedUserIdOf(authentication) : null;
    }

    private static boolean isPrivileged(Authentication authentication) {
        StaffRole role = StaffPrincipals.roleOrNull(authentication);
        return role == StaffRole.ADMIN || role == StaffRole.CAMPAIGN_CURATOR;
    }

    @Operation(summary = "List collaborators on a campaign")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{campaignId}/collaborators")
    public ResponseEntity<List<CampaignCollaboratorResponse>> listCollaborators(
            @PathVariable UUID campaignId,
            Authentication authentication) {
        return ResponseEntity.ok(collaboratorService.listCollaborators(
                viewerId(authentication), campaignId, isPrivileged(authentication)));
    }

    @Operation(summary = "Invite a player to collaborate on a campaign you own")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/collaborators")
    public ResponseEntity<CampaignCollaboratorResponse> inviteCollaborator(
            @PathVariable UUID campaignId,
            @Valid @RequestBody InviteCampaignCollaboratorRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                collaboratorService.invite(principal.getUserId(), campaignId, request.getUserId()));
    }

    @Operation(summary = "Accept a pending collaboration invite")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/collaborators/accept")
    public ResponseEntity<CampaignCollaboratorResponse> acceptInvite(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(collaboratorService.respond(principal.getUserId(), campaignId, true));
    }

    @Operation(summary = "Decline a pending collaboration invite")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/collaborators/decline")
    public ResponseEntity<CampaignCollaboratorResponse> declineInvite(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(collaboratorService.respond(principal.getUserId(), campaignId, false));
    }

    @Operation(summary = "Remove a collaborator (owner) or leave a campaign (self)")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/collaborators/{userId}")
    public ResponseEntity<Void> removeCollaborator(
            @PathVariable UUID campaignId,
            @PathVariable Long userId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        collaboratorService.remove(principal.getUserId(), campaignId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List the authenticated player's campaign collaborations and invites")
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
