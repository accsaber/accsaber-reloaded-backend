package com.accsaber.backend.controller.campaign;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.campaign.CampaignLeaderboardEntry;
import com.accsaber.backend.model.dto.response.campaign.CampaignNodeScoreEntry;
import com.accsaber.backend.model.entity.campaign.CampaignLeaderboardBoard;
import com.accsaber.backend.service.campaign.CampaignLeaderboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/campaigns/{campaignId}/leaderboard")
@RequiredArgsConstructor
@Tag(name = "Campaigns")
public class CampaignLeaderboardController {

    private final CampaignLeaderboardService campaignLeaderboardService;

    @Operation(summary = "Campaign leaderboard", description = "Pick the board with the board param: first to "
            + "finish, best average acc, best average AP or furthest progress.")
    @GetMapping
    public ResponseEntity<Page<CampaignLeaderboardEntry>> getBoard(
            @PathVariable UUID campaignId,
            @RequestParam(defaultValue = "COMPLETIONS") CampaignLeaderboardBoard board,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(campaignLeaderboardService.getBoard(campaignId, board, search, pageable));
    }

    @Operation(summary = "Node leaderboard")
    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<Page<CampaignNodeScoreEntry>> getNodeBoard(
            @PathVariable UUID campaignId,
            @PathVariable UUID nodeId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(campaignLeaderboardService.getNodeBoard(campaignId, nodeId, pageable));
    }
}
