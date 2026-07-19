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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.model.dto.request.campaign.AddCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignTextRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignTagRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignBarrierRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.request.map.ImportCampaignMapRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignBarrierResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTextResponse;
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
    private static final String CAMPAIGN_CHECKPOINT_SUBDIR = "campaign-checkpoints";

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

    @Operation(summary = "Mark a campaign official (allows its creators to reward untradeable items)")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{campaignId}/official")
    public ResponseEntity<CampaignResponse> setOfficial(
            @PathVariable UUID campaignId,
            @RequestParam(name = "value", defaultValue = "true") boolean official) {
        return ResponseEntity.ok(campaignService.setOfficial(campaignId, official));
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

    @Operation(summary = "Point a campaign node at different leaderboard IDs")
    @PutMapping("/difficulties/{campaignDifficultyId}/map")
    public ResponseEntity<CampaignDifficultyResponse> updateDifficultyMap(
            @PathVariable UUID campaignDifficultyId,
            @Valid @RequestBody ImportCampaignMapRequest request) {
        return ResponseEntity.ok(campaignService.updateDifficultyMap(campaignDifficultyId, request));
    }

    @Operation(summary = "Remove a difficulty from a campaign")
    @PatchMapping("/{campaignId}/difficulties/{campaignDifficultyId}/deactivate")
    public ResponseEntity<Void> removeDifficulty(
            @PathVariable UUID campaignId,
            @PathVariable UUID campaignDifficultyId) {
        campaignService.removeDifficulty(campaignId, campaignDifficultyId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add a barrier to a campaign")
    @PostMapping("/{campaignId}/barriers")
    public ResponseEntity<CampaignBarrierResponse> addBarrier(
            @PathVariable UUID campaignId,
            @Valid @RequestBody AddCampaignBarrierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.addBarrier(campaignId, request));
    }

    @Operation(summary = "Update a campaign barrier")
    @PatchMapping("/barriers/{barrierId}")
    public ResponseEntity<CampaignBarrierResponse> updateBarrier(
            @PathVariable UUID barrierId,
            @Valid @RequestBody UpdateCampaignBarrierRequest request) {
        return ResponseEntity.ok(campaignService.updateBarrier(barrierId, request));
    }

    @Operation(summary = "Remove a barrier from a campaign")
    @PatchMapping("/{campaignId}/barriers/{barrierId}/deactivate")
    public ResponseEntity<Void> removeBarrier(
            @PathVariable UUID campaignId,
            @PathVariable UUID barrierId) {
        campaignService.removeDifficulty(campaignId, barrierId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add a freeform text element to a campaign")
    @PostMapping("/{campaignId}/texts")
    public ResponseEntity<CampaignTextResponse> addText(
            @PathVariable UUID campaignId,
            @Valid @RequestBody CampaignTextRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.addText(campaignId, request));
    }

    @Operation(summary = "Update a campaign freeform text element")
    @PatchMapping("/texts/{textId}")
    public ResponseEntity<CampaignTextResponse> updateText(
            @PathVariable UUID textId,
            @Valid @RequestBody CampaignTextRequest request) {
        return ResponseEntity.ok(campaignService.updateText(textId, request));
    }

    @Operation(summary = "Remove a freeform text element from a campaign")
    @PatchMapping("/{campaignId}/texts/{textId}/deactivate")
    public ResponseEntity<Void> removeText(
            @PathVariable UUID campaignId,
            @PathVariable UUID textId) {
        campaignService.removeText(campaignId, textId);
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
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_BACKGROUND_SUBDIR, campaignId.toString(),
                MediaFormat.PNG);
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

    @Operation(summary = "Upload (or replace) the milestone avatar for a campaign node")
    @PostMapping(value = "/difficulties/{campaignDifficultyId}/checkpoint-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignDifficultyResponse> uploadNodeCheckpointAvatar(
            @PathVariable UUID campaignDifficultyId,
            @RequestPart("file") MultipartFile file) {
        String url = mediaProcessingService.storeImage(file, CAMPAIGN_CHECKPOINT_SUBDIR,
                campaignDifficultyId.toString(), MediaFormat.PNG);
        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
        request.setCheckpointAvatarUrl(url);
        return ResponseEntity.ok(campaignService.updateDifficulty(campaignDifficultyId, request));
    }

    @Operation(summary = "Remove the milestone avatar for a campaign node")
    @DeleteMapping("/difficulties/{campaignDifficultyId}/checkpoint-avatar")
    public ResponseEntity<CampaignDifficultyResponse> deleteNodeCheckpointAvatar(
            @PathVariable UUID campaignDifficultyId) {
        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
        request.setCheckpointAvatarUrl("");
        CampaignDifficultyResponse result = campaignService.updateDifficulty(campaignDifficultyId, request);
        mediaProcessingService.deleteIfExists(CAMPAIGN_CHECKPOINT_SUBDIR, campaignDifficultyId.toString());
        return ResponseEntity.ok(result);
    }
}
