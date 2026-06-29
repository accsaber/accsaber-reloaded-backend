package com.accsaber.backend.controller.admin;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.model.dto.request.campaign.AddCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignTagRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.campaign.CampaignService;
import com.accsaber.backend.service.media.MediaFormat;
import com.accsaber.backend.service.media.MediaProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/campaigns")
@PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_CURATOR')")
@RequiredArgsConstructor
@Tag(name = "Admin Campaigns")
public class AdminCampaignController {

    private static final String CAMPAIGN_BACKGROUND_SUBDIR = "campaigns";
    private static final String CAMPAIGN_ICON_SUBDIR = "campaign-icons";

    private final CampaignService campaignService;
    private final MediaProcessingService mediaProcessingService;

    @Operation(summary = "Create a campaign")
    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(@Valid @RequestBody CreateCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(request));
    }

    @Operation(summary = "Update a campaign")
    @PatchMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> updateCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody UpdateCampaignRequest request) {
        return ResponseEntity.ok(campaignService.updateCampaign(campaignId, request));
    }

    @Operation(summary = "Publish a campaign")
    @PatchMapping("/{campaignId}/publish")
    public ResponseEntity<CampaignResponse> publish(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.publish(campaignId));
    }

    @Operation(summary = "Move a published campaign back into editing")
    @PatchMapping("/{campaignId}/edit")
    public ResponseEntity<CampaignResponse> startEditing(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.startEditing(campaignId));
    }

    @Operation(summary = "Mark a campaign as curated")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_CURATOR')")
    @PatchMapping("/{campaignId}/curate")
    public ResponseEntity<CampaignResponse> markCurated(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal StaffUserDetails principal) {
        return ResponseEntity.ok(campaignService.markCurated(campaignId, principal.getStaffUser()));
    }

    @Operation(summary = "Strip curation status from a campaign")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_CURATOR')")
    @PatchMapping("/{campaignId}/uncurate")
    public ResponseEntity<CampaignResponse> uncurate(
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal StaffUserDetails principal) {
        return ResponseEntity.ok(campaignService.uncurate(campaignId, principal.getStaffUser()));
    }

    @Operation(summary = "Deactivate a campaign")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{campaignId}/deactivate")
    public ResponseEntity<Void> deactivateCampaign(@PathVariable UUID campaignId) {
        campaignService.deactivateCampaign(campaignId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add a difficulty to a campaign")
    @PostMapping("/{campaignId}/difficulties")
    public ResponseEntity<CampaignDifficultyResponse> addDifficulty(
            @PathVariable UUID campaignId,
            @Valid @RequestBody AddCampaignDifficultyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.addDifficulty(campaignId, request));
    }

    @Operation(summary = "Update a campaign difficulty")
    @PatchMapping("/difficulties/{campaignDifficultyId}")
    public ResponseEntity<CampaignDifficultyResponse> updateDifficulty(
            @PathVariable UUID campaignDifficultyId,
            @Valid @RequestBody UpdateCampaignDifficultyRequest request) {
        return ResponseEntity.ok(campaignService.updateDifficulty(campaignDifficultyId, request));
    }

    @Operation(summary = "Remove a difficulty from a campaign")
    @PatchMapping("/{campaignId}/difficulties/{campaignDifficultyId}/deactivate")
    public ResponseEntity<Void> removeDifficulty(
            @PathVariable UUID campaignId,
            @PathVariable UUID campaignDifficultyId) {
        campaignService.removeDifficulty(campaignId, campaignDifficultyId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Create a campaign tag")
    @PostMapping("/tags")
    public ResponseEntity<CampaignTagResponse> createTag(
            @Valid @RequestBody CreateCampaignTagRequest request,
            @AuthenticationPrincipal StaffUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.createTag(request, principal != null ? principal.getStaffUser() : null));
    }

    @Operation(summary = "Upload (or replace) the background image for a campaign")
    @PostMapping(value = "/{campaignId}/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignResponse> uploadBackground(
            @PathVariable UUID campaignId,
            @RequestPart("file") MultipartFile file) {
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_BACKGROUND_SUBDIR, campaignId.toString());
        return ResponseEntity.ok(campaignService.setBackgroundUrl(campaignId, url));
    }

    @Operation(summary = "Remove the background image for a campaign")
    @DeleteMapping("/{campaignId}/background")
    public ResponseEntity<CampaignResponse> deleteBackground(@PathVariable UUID campaignId) {
        mediaProcessingService.deleteIfExists(CAMPAIGN_BACKGROUND_SUBDIR, campaignId.toString());
        return ResponseEntity.ok(campaignService.setBackgroundUrl(campaignId, null));
    }

    @Operation(summary = "Upload (or replace) the icon image for a campaign")
    @PostMapping(value = "/{campaignId}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignResponse> uploadIcon(
            @PathVariable UUID campaignId,
            @RequestPart("file") MultipartFile file) {
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_ICON_SUBDIR, campaignId.toString(),
                MediaFormat.PNG);
        return ResponseEntity.ok(campaignService.setIconUrl(campaignId, url));
    }

    @Operation(summary = "Remove the icon image for a campaign")
    @DeleteMapping("/{campaignId}/icon")
    public ResponseEntity<CampaignResponse> deleteIcon(@PathVariable UUID campaignId) {
        mediaProcessingService.deleteIfExists(CAMPAIGN_ICON_SUBDIR, campaignId.toString());
        return ResponseEntity.ok(campaignService.setIconUrl(campaignId, null));
    }
}
