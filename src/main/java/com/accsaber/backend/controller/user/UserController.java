package com.accsaber.backend.controller.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.dto.response.milestone.UserMilestoneProgressResponse;
import com.accsaber.backend.model.dto.response.player.NameHistoryResponse;
import com.accsaber.backend.model.dto.response.player.UserCategoryStatisticsResponse;
import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.service.campaign.CampaignService;
import com.accsaber.backend.service.milestone.LevelService;
import com.accsaber.backend.service.milestone.MilestoneService;
import com.accsaber.backend.service.player.UserService;
import com.accsaber.backend.service.score.ScoreService;
import com.accsaber.backend.service.stats.StatisticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Players")
public class UserController {

    private final UserService userService;
    private final ScoreService scoreService;
    private final StatisticsService statisticsService;
    private final MilestoneService milestoneService;
    private final LevelService levelService;
    private final CampaignService campaignService;

    @Operation(summary = "Get user profile", description = "Returns a player profile by Steam ID")
    @GetMapping("/{steamId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long steamId) {
        return ResponseEntity.ok(userService.findBySteamId(steamId));
    }

    @Operation(summary = "Get user name history", description = "Returns a player's previous names, most recent first")
    @GetMapping("/{steamId}/name-history")
    public ResponseEntity<List<NameHistoryResponse>> getNameHistory(@PathVariable Long steamId) {
        List<NameHistoryResponse> history = userService.getNameHistory(steamId).stream()
                .map(h -> new NameHistoryResponse(h.getName(), h.getChangedAt()))
                .toList();
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Get user category statistics", description = "Returns all active category statistics for a player")
    @GetMapping("/{steamId}/statistics")
    public ResponseEntity<List<UserCategoryStatisticsResponse>> getUserStatistics(@PathVariable Long steamId) {
        return ResponseEntity.ok(statisticsService.findByUser(steamId));
    }

    @Operation(summary = "Get user scores", description = "Paginated list of a player's active scores, optionally filtered by category and/or song name")
    @GetMapping("/{steamId}/scores")
    public ResponseEntity<Page<ScoreResponse>> getUserScores(
            @PathVariable Long steamId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "ap", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(scoreService.findByUser(steamId, categoryId, search, pageable));
    }

    @Operation(summary = "Get user milestone progress")
    @GetMapping("/{steamId}/milestones")
    public ResponseEntity<Page<UserMilestoneProgressResponse>> getUserMilestones(
            @PathVariable Long steamId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findUserProgress(steamId, pageable));
    }

    @Operation(summary = "Get user level and XP")
    @GetMapping("/{steamId}/level")
    public ResponseEntity<LevelResponse> getUserLevel(@PathVariable Long steamId) {
        var totalXp = userService.getTotalXp(steamId);
        return ResponseEntity.ok(levelService.calculateLevel(totalXp));
    }

    @Operation(summary = "Get user progress in a campaign")
    @GetMapping("/{steamId}/campaigns/{campaignId}")
    public ResponseEntity<CampaignProgressResponse> getUserCampaignProgress(
            @PathVariable Long steamId,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.getUserProgress(steamId, campaignId));
    }
}
