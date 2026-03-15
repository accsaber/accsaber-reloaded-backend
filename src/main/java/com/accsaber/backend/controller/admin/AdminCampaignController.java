package com.accsaber.backend.controller.admin;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.campaign.AddCampaignMapRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignMapResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.service.campaign.CampaignService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/campaigns")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Campaigns")
public class AdminCampaignController {

    private final CampaignService campaignService;

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

    @Operation(summary = "Deactivate a campaign")
    @PatchMapping("/{campaignId}/deactivate")
    public ResponseEntity<Void> deactivateCampaign(@PathVariable UUID campaignId) {
        campaignService.deactivateCampaign(campaignId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add a map to a campaign")
    @PostMapping("/{campaignId}/maps")
    public ResponseEntity<CampaignMapResponse> addCampaignMap(
            @PathVariable UUID campaignId,
            @Valid @RequestBody AddCampaignMapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.addCampaignMap(campaignId, request));
    }

    @Operation(summary = "Remove a map from a campaign")
    @PatchMapping("/{campaignId}/maps/{campaignMapId}/deactivate")
    public ResponseEntity<Void> removeCampaignMap(
            @PathVariable UUID campaignId,
            @PathVariable UUID campaignMapId) {
        campaignService.removeCampaignMap(campaignId, campaignMapId);
        return ResponseEntity.noContent().build();
    }
}
