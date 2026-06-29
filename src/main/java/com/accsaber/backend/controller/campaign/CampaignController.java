package com.accsaber.backend.controller.campaign;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.model.dto.request.campaign.AddCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.SetCampaignItemRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignItemAwardResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.model.dto.response.campaign.UserCampaignResponse;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.CampaignTagKind;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.campaign.CampaignService;
import com.accsaber.backend.service.media.MediaFormat;
import com.accsaber.backend.service.media.MediaProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns")
public class CampaignController {

    private static final String CAMPAIGN_BACKGROUND_SUBDIR = "campaigns";
    private static final String CAMPAIGN_ICON_SUBDIR = "campaign-icons";

    private final CampaignService campaignService;
    private final MediaProcessingService mediaProcessingService;

    private static Long viewerId(Authentication authentication) {
        return authentication != null ? StaffPrincipals.linkedUserIdOf(authentication) : null;
    }

    private static boolean canViewAllDrafts(Authentication authentication) {
        StaffRole role = StaffPrincipals.roleOrNull(authentication);
        return role == StaffRole.ADMIN || role == StaffRole.CAMPAIGN_CURATOR;
    }

    @Operation(summary = "List active campaigns")
    @GetMapping
    public ResponseEntity<Page<CampaignResponse>> listCampaigns(
            @RequestParam(required = false) List<CampaignStatus> status,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) Long creatorId,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(campaignService.findCampaigns(status, tagIds, creatorId,
                viewerId(authentication), canViewAllDrafts(authentication), pageable));
    }

    @Operation(summary = "List campaigns seeking curation")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_CURATOR')")
    @GetMapping("/curation-queue")
    public ResponseEntity<Page<CampaignResponse>> listCurationQueue(
            @PageableDefault(size = 20, sort = "submittedAt") Pageable pageable) {
        return ResponseEntity.ok(campaignService.findCurationQueue(pageable));
    }

    @Operation(summary = "Get campaign details by id")
    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> getCampaign(
            @PathVariable UUID campaignId,
            Authentication authentication) {
        return ResponseEntity.ok(campaignService.findCampaignById(campaignId,
                viewerId(authentication), canViewAllDrafts(authentication)));
    }

    @Operation(summary = "Get campaign details by slug")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<CampaignDetailResponse> getCampaignBySlug(
            @PathVariable String slug,
            Authentication authentication) {
        return ResponseEntity.ok(campaignService.findCampaignBySlug(slug,
                viewerId(authentication), canViewAllDrafts(authentication)));
    }

    @Operation(summary = "List campaign tags")
    @GetMapping("/tags")
    public ResponseEntity<List<CampaignTagResponse>> listTags(
            @RequestParam(required = false) CampaignTagKind kind) {
        return ResponseEntity.ok(kind != null ? campaignService.listTagsByKind(kind) : campaignService.listTags());
    }

    @Operation(summary = "Start a campaign as the authenticated player")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/start")
    public ResponseEntity<UserCampaignResponse> startCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.startCampaign(principal.getUserId(), campaignId));
    }

    @Operation(summary = "Abandon a campaign as the authenticated player")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/start")
    public ResponseEntity<Void> abandonCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.abandonCampaign(principal.getUserId(), campaignId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List the authenticated player's campaigns")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<Page<UserCampaignResponse>> listMyCampaigns(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(campaignService.listUserCampaigns(principal.getUserId(), pageable));
    }

    @Operation(summary = "Get the authenticated player's progress in a campaign")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{campaignId}/me/progress")
    public ResponseEntity<CampaignProgressResponse> getMyProgress(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.getUserProgress(principal.getUserId(), campaignId));
    }

    @Operation(summary = "Get the authenticated player's progress for multiple campaigns")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/progress")
    public ResponseEntity<List<CampaignProgressResponse>> getMyProgressBulk(
            @RequestParam("ids") List<UUID> ids,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.getUserProgressBulk(principal.getUserId(), ids));
    }

    @Operation(summary = "Create a draft campaign as the authenticated player")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<CampaignResponse> createMyCampaign(
            @Valid @RequestBody CreateCampaignRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.createCampaignAsPlayer(principal.getUserId(), request));
    }

    @Operation(summary = "Update a draft campaign the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> updateMyCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody UpdateCampaignRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(
                campaignService.updateCampaignAsPlayer(principal.getUserId(), campaignId, request));
    }

    @Operation(summary = "Publish a campaign the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}/publish")
    public ResponseEntity<CampaignResponse> publishMyCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.publishAsPlayer(principal.getUserId(), campaignId));
    }

    @Operation(summary = "Unpublish a campaign the authenticated player owns, returning it to draft for editing")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}/unpublish")
    public ResponseEntity<CampaignResponse> unpublishMyCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.unpublishAsPlayer(principal.getUserId(), campaignId));
    }

    @Operation(summary = "Soft-delete a draft campaign the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}")
    public ResponseEntity<Void> deactivateMyCampaign(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.deactivateCampaignAsPlayer(principal.getUserId(), campaignId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Submit (or retract) a draft campaign for curator review")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{campaignId}/submit")
    public ResponseEntity<CampaignResponse> submitMyCampaignForCuration(
            @PathVariable UUID campaignId,
            @RequestParam(name = "seeking", defaultValue = "true") boolean seeking,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(
                campaignService.submitForCurationAsPlayer(principal.getUserId(), campaignId, seeking));
    }

    @Operation(summary = "Add a difficulty to a draft campaign the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/difficulties")
    public ResponseEntity<CampaignDifficultyResponse> addDifficultyToMyCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody AddCampaignDifficultyRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.addDifficultyAsPlayer(principal.getUserId(), campaignId, request));
    }

    @Operation(summary = "Update a difficulty on a draft campaign the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/difficulties/{campaignDifficultyId}")
    public ResponseEntity<CampaignDifficultyResponse> updateDifficultyOnMyCampaign(
            @PathVariable UUID campaignDifficultyId,
            @Valid @RequestBody UpdateCampaignDifficultyRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(
                campaignService.updateDifficultyAsPlayer(principal.getUserId(), campaignDifficultyId, request));
    }

    @Operation(summary = "Remove a difficulty from a draft campaign the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/difficulties/{campaignDifficultyId}")
    public ResponseEntity<Void> removeDifficultyFromMyCampaign(
            @PathVariable UUID campaignId,
            @PathVariable UUID campaignDifficultyId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        campaignService.removeDifficultyAsPlayer(principal.getUserId(), campaignId, campaignDifficultyId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Attach or update an item reward on a campaign difficulty (node) the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/difficulties/{campaignDifficultyId}/items")
    public ResponseEntity<List<CampaignItemAwardResponse>> setDifficultyItemOnMyCampaign(
            @PathVariable UUID campaignDifficultyId,
            @Valid @RequestBody SetCampaignItemRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.setDifficultyItemAsPlayer(
                principal.getUserId(), campaignDifficultyId, request));
    }

    @Operation(summary = "Remove an item reward from a campaign difficulty (node) the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/difficulties/{campaignDifficultyId}/items/{itemId}")
    public ResponseEntity<List<CampaignItemAwardResponse>> removeDifficultyItemFromMyCampaign(
            @PathVariable UUID campaignDifficultyId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.removeDifficultyItemAsPlayer(
                principal.getUserId(), campaignDifficultyId, itemId));
    }

    @Operation(summary = "Attach or update an item reward on the campaign's completion bonus")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{campaignId}/completion-items")
    public ResponseEntity<List<CampaignItemAwardResponse>> setCompletionItemOnMyCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody SetCampaignItemRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.setCompletionItemAsPlayer(
                principal.getUserId(), campaignId, request));
    }

    @Operation(summary = "Remove an item reward from the campaign's completion bonus")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/completion-items/{itemId}")
    public ResponseEntity<List<CampaignItemAwardResponse>> removeCompletionItemFromMyCampaign(
            @PathVariable UUID campaignId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(campaignService.removeCompletionItemAsPlayer(
                principal.getUserId(), campaignId, itemId));
    }

    @Operation(summary = "Upload (or replace) the background image for a campaign the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{campaignId}/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignResponse> uploadMyCampaignBackground(
            @PathVariable UUID campaignId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_BACKGROUND_SUBDIR, campaignId.toString());
        return ResponseEntity.ok(
                campaignService.setBackgroundUrlAsPlayer(principal.getUserId(), campaignId, url));
    }

    @Operation(summary = "Remove the background image for a campaign the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/background")
    public ResponseEntity<CampaignResponse> deleteMyCampaignBackground(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        CampaignResponse result = campaignService.setBackgroundUrlAsPlayer(principal.getUserId(), campaignId, null);
        mediaProcessingService.deleteIfExists(CAMPAIGN_BACKGROUND_SUBDIR, campaignId.toString());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Upload (or replace) the icon image for a campaign the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{campaignId}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignResponse> uploadMyCampaignIcon(
            @PathVariable UUID campaignId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_ICON_SUBDIR, campaignId.toString(),
                MediaFormat.PNG);
        return ResponseEntity.ok(
                campaignService.setIconUrlAsPlayer(principal.getUserId(), campaignId, url));
    }

    @Operation(summary = "Remove the icon image for a campaign the authenticated player owns")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{campaignId}/icon")
    public ResponseEntity<CampaignResponse> deleteMyCampaignIcon(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        CampaignResponse result = campaignService.setIconUrlAsPlayer(principal.getUserId(), campaignId, null);
        mediaProcessingService.deleteIfExists(CAMPAIGN_ICON_SUBDIR, campaignId.toString());
        return ResponseEntity.ok(result);
    }
}
