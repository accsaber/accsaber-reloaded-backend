package com.accsaber.backend.controller.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.security.StaffPrincipals;

import com.accsaber.backend.model.dto.request.campaign.CampaignFilter;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.UserCampaignResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.dto.response.milestone.UserMilestoneProgressResponse;
import com.accsaber.backend.model.dto.response.player.NameHistoryResponse;
import com.accsaber.backend.model.dto.response.player.PinnedScoreResponse;
import com.accsaber.backend.model.dto.response.player.RankingHistoryResponse;
import com.accsaber.backend.model.dto.response.player.StatsDiffResponse;
import com.accsaber.backend.model.dto.response.player.UserAllStatisticsResponse;
import com.accsaber.backend.model.dto.response.player.UserCategoryStatisticsResponse;
import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.score.UserScoreSummaryResponse;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.service.campaign.CampaignService;
import com.accsaber.backend.service.map.MapService;
import com.accsaber.backend.service.milestone.LevelService;
import com.accsaber.backend.service.infra.CategoryService;
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
    private final MapService mapService;
    private final MilestoneService milestoneService;
    private final LevelService levelService;
    private final CampaignService campaignService;
    private final CategoryService categoryService;

    @Operation(summary = "Player profile", description = "statistics=true adds all category stats. "
            + "blockedCount only shows on your own profile.")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean statistics,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long viewerId = principal != null ? principal.getUserId() : null;
        UserResponse user = userService.findByUserId(userId, viewerId);
        if (statistics) {
            user = user.withStatistics(statisticsService.findCategoryStatsByUser(userId));
        }
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Name history", description = "Older names, newest first.")
    @GetMapping("/{userId}/name-history")
    public ResponseEntity<List<NameHistoryResponse>> getNameHistory(@PathVariable Long userId) {
        List<NameHistoryResponse> history = userService.getNameHistory(userId).stream()
                .map(h -> new NameHistoryResponse(h.getName(), h.getChangedAt()))
                .toList();
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Pinned scores", description = "In display order. Four max, eight for "
            + "supporters.")
    @GetMapping("/{userId}/pinned-scores")
    public ResponseEntity<List<PinnedScoreResponse>> getPinnedScores(@PathVariable Long userId) {
        List<PinnedScoreResponse> pinned = userService.getPinnedScores(userId).stream()
                .map(pin -> PinnedScoreResponse.builder()
                        .score(scoreService.mapToResponse(pin.getScore()))
                        .comment(pin.getComment())
                        .build())
                .toList();
        return ResponseEntity.ok(pinned);
    }

    @Operation(summary = "Pinned milestones", description = "In display order. Four max, eight for "
            + "supporters.")
    @GetMapping("/{userId}/pinned-milestones")
    public ResponseEntity<List<UserMilestoneProgressResponse>> getPinnedMilestones(@PathVariable Long userId) {
        return ResponseEntity.ok(milestoneService.findPinnedByUser(userId));
    }

    @Operation(summary = "Stats in every category", description = "Also has the XP breakdown, their "
            + "clan, rank there and war record across every clan. Use this instead of looping the single category "
            + "route.")
    @GetMapping("/{userId}/statistics/all")
    public ResponseEntity<UserAllStatisticsResponse> getAllUserStatistics(@PathVariable Long userId) {
        return ResponseEntity.ok(statisticsService.findAllByUser(userId));
    }

    @Operation(summary = "Stats in one category", description = "Category code, like true_acc. Leave "
            + "it off for overall.")
    @GetMapping("/{userId}/statistics")
    public ResponseEntity<UserCategoryStatisticsResponse> getUserStatistics(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category) {
        return ResponseEntity.ok(statisticsService.findByUserAndCategoryCode(userId, category));
    }

    @Operation(summary = "Stats history", description = "Every snapshot in the range, oldest first. "
            + "unit is h, d, w or mo. amount is how many to go back.")
    @GetMapping("/{userId}/statistics/historic")
    public ResponseEntity<List<UserCategoryStatisticsResponse>> getUserStatisticsHistoric(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(statisticsService.findHistoric(userId, category, amount, unit));
    }

    @Operation(summary = "Rank history", description = "Daily snapshots, oldest first. Lighter than "
            + "stats history. Same unit and amount params.")
    @GetMapping("/{userId}/ranking-history")
    public ResponseEntity<List<RankingHistoryResponse>> getUserRankingHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(statisticsService.findRankingHistory(userId, category, amount, unit));
    }

    @Operation(summary = "24 hour gains", description = "AP, rank and accuracy change since "
            + "yesterday. 204 when there is no baseline, like a new player.")
    @GetMapping("/{userId}/stats-diff")
    public ResponseEntity<StatsDiffResponse> getStatsDiff(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category) {
        Optional<StatsDiffResponse> diff = statisticsService.computeStatsDiff(userId, category);
        return diff.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Score history on a difficulty", description = "Every version in the "
            + "range, oldest first. Each improvement is its own entry.")
    @GetMapping("/{userId}/scores/historic")
    public ResponseEntity<List<ScoreResponse>> getUserScoresHistoric(
            @PathVariable Long userId,
            @RequestParam UUID mapDifficultyId,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(scoreService.findHistoric(userId, mapDifficultyId, amount, unit));
    }

    @Operation(summary = "Score by song hash", description = "difficulty is required, one of EASY, "
            + "NORMAL, HARD, EXPERT or EXPERT_PLUS. Characteristic defaults to Standard.")
    @GetMapping("/{userId}/scores/by-hash/{songHash}")
    public ResponseEntity<ScoreResponse> getUserScoreBySongHash(
            @PathVariable Long userId,
            @PathVariable String songHash,
            @RequestParam Difficulty difficulty,
            @RequestParam(defaultValue = "Standard") String characteristic) {
        return ResponseEntity
                .ok(scoreService.findActiveByUserAndSongHash(userId, songHash, difficulty, characteristic));
    }

    @Operation(summary = "Player scores", description = "Current scores, best AP first. Filter by category "
            + "UUID or code, or song name. maxStreak115, playCount and lastPlayedAt count every attempt.")
    @GetMapping("/{userId}/scores")
    public ResponseEntity<Page<ScoreResponse>> getUserScores(
            @PathVariable Long userId,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "ap", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity
                .ok(scoreService.findByUser(userId, categoryService.resolveId(categoryId), search, pageable));
    }

    @Operation(summary = "All scores, no paging", description = "Trimmed fields, best AP first. The "
            + "plugin uses this to fill its cache.")
    @GetMapping("/{userId}/scores/all")
    public ResponseEntity<List<UserScoreSummaryResponse>> getAllUserScores(@PathVariable Long userId) {
        return ResponseEntity.ok(scoreService.findAllSummariesByUser(userId));
    }

    @Operation(summary = "Milestone progress")
    @GetMapping("/{userId}/milestones")
    public ResponseEntity<Page<UserMilestoneProgressResponse>> getUserMilestones(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findUserProgress(userId, pageable));
    }

    @Operation(summary = "Finished milestones", description = "Flat list, each with when they "
            + "got it.")
    @GetMapping("/{userId}/milestones/completed")
    public ResponseEntity<List<UserMilestoneProgressResponse>> getUserCompletedMilestones(
            @PathVariable Long userId) {
        return ResponseEntity.ok(milestoneService.findCompletedByUser(userId));
    }

    @Operation(summary = "Milestones left", description = "Flat list with current "
            + "progress.")
    @GetMapping("/{userId}/milestones/uncompleted")
    public ResponseEntity<List<UserMilestoneProgressResponse>> getUserUncompletedMilestones(
            @PathVariable Long userId) {
        return ResponseEntity.ok(milestoneService.findUncompletedByUser(userId));
    }

    @Operation(summary = "Level and XP", description = "Thresholds are configurable. Read them from "
            + "here instead of assuming a formula.")
    @GetMapping("/{userId}/level")
    public ResponseEntity<LevelResponse> getUserLevel(@PathVariable Long userId) {
        var totalXp = userService.getTotalXp(userId);
        return ResponseEntity.ok(levelService.calculateLevel(totalXp));
    }

    @Operation(summary = "Unplayed maps", description = "Ranked difficulties with no score "
            + "yet. Same filters as the difficulty list.")
    @GetMapping("/{userId}/missing-maps")
    public ResponseEntity<Page<PublicMapDifficultyResponse>> getMissingMaps(
            @PathVariable Long userId,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) List<MapDifficultyStatus> status,
            @RequestParam(required = false) Double complexityMin,
            @RequestParam(required = false) Double complexityMax,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "rankedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(mapService.findDifficultiesPublic(categoryService.resolveId(categoryId), status,
                complexityMin, complexityMax, search, userId, pageable));
    }

    @Operation(summary = "Maps above an AP mark", description = "Difficulties where "
            + "they have at least apMin, as a flat list. Can scope to one category.")
    @GetMapping("/{userId}/maps-above-ap")
    public ResponseEntity<List<PublicMapDifficultyResponse>> getMapsAboveAp(
            @PathVariable Long userId,
            @RequestParam Double apMin,
            @RequestParam(required = false) String categoryId) {
        return ResponseEntity.ok(mapService.findDifficultiesWithUserScoreAbovePublic(userId, apMin,
                categoryService.resolveId(categoryId)));
    }

    @Operation(summary = "Player's campaigns", description = "Same filters and sorting as the campaign list. "
            + "progressStatus picks in progress or finished. Signing in only adds your own vote.")
    @GetMapping("/{userId}/campaigns")
    public ResponseEntity<Page<UserCampaignResponse>> listUserCampaigns(
            @PathVariable Long userId,
            @RequestParam(required = false) List<CampaignStatus> status,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean official,
            @RequestParam(required = false) Boolean loved,
            @RequestParam(required = false) List<UserCampaignStatus> progressStatus,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        CampaignFilter filter = CampaignFilter.builder()
                .status(status).tagIds(tagIds).creatorId(creatorId).search(search)
                .official(official).loved(loved)
                .participantId(userId).progressStatus(progressStatus).build();
        return ResponseEntity.ok(campaignService.listUserCampaigns(filter,
                StaffPrincipals.viewerIdOf(authentication),
                StaffPrincipals.canViewCampaignDrafts(authentication), pageable));
    }

    @Operation(summary = "Campaign progress", description = "Per node, with unlocks and bests. "
            + "For yourself, use the campaign me route.")
    @GetMapping("/{userId}/campaigns/{campaignId}")
    public ResponseEntity<CampaignProgressResponse> getUserCampaignProgress(
            @PathVariable Long userId,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.getUserProgress(userId, campaignId));
    }
}
