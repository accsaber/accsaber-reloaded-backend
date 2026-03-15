package com.accsaber.backend.controller.campaign;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.service.campaign.CampaignService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    @Operation(summary = "List all active campaigns")
    @GetMapping
    public ResponseEntity<Page<CampaignResponse>> listCampaigns(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(campaignService.findAllActiveCampaigns(pageable));
    }

    @Operation(summary = "Get campaign details with maps")
    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> getCampaign(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.findCampaignById(campaignId));
    }
}
