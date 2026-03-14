package com.accsaber.backend.controller.milestone;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.campaign.AddCampaignMapRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignMapResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.service.campaign.CampaignService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    @Operation(summary = "List all active campaigns")
    @GetMapping("/campaigns")
    public ResponseEntity<Page<CampaignResponse>> getAllCampaigns(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(campaignService.findAllActiveCampaigns(pageable));
    }

    @Operation(summary = "Get campaign details with maps")
    @GetMapping("/campaigns/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> getCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.findCampaignById(campaignId));
    }

    @Operation(summary = "Get user progress in a campaign")
    @GetMapping("/users/{steamId}/campaigns/{campaignId}")
    public ResponseEntity<CampaignProgressResponse> getUserProgress(
            @PathVariable Long steamId,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.getUserProgress(steamId, campaignId));
    }

    @Operation(summary = "Create a campaign")
    @PostMapping("/admin/campaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CampaignResponse> createCampaign(@Valid @RequestBody CreateCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(request));
    }

    @Operation(summary = "Update a campaign")
    @PatchMapping("/admin/campaigns/{campaignId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CampaignResponse> updateCampaign(
            @PathVariable UUID campaignId,
            @Valid @RequestBody UpdateCampaignRequest request) {
        return ResponseEntity.ok(campaignService.updateCampaign(campaignId, request));
    }

    @Operation(summary = "Deactivate a campaign")
    @PatchMapping("/admin/campaigns/{campaignId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCampaign(@PathVariable UUID campaignId) {
        campaignService.deactivateCampaign(campaignId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add a map to a campaign")
    @PostMapping("/admin/campaigns/{campaignId}/maps")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CampaignMapResponse> addCampaignMap(
            @PathVariable UUID campaignId,
            @Valid @RequestBody AddCampaignMapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.addCampaignMap(campaignId, request));
    }

    @Operation(summary = "Remove a map from a campaign")
    @PatchMapping("/admin/campaigns/{campaignId}/maps/{campaignMapId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeCampaignMap(
            @PathVariable UUID campaignId,
            @PathVariable UUID campaignMapId) {
        campaignService.removeCampaignMap(campaignId, campaignMapId);
        return ResponseEntity.noContent().build();
    }
}
