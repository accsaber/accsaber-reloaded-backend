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

    @Operation(summary = "Get a player profile", description = "A player by their user ID. Pass statistics=true if you also want "
            + "all their category stats in the same response, which saves you a second call. Relation counts come back either "
            + "way, though blockedCount only appears when the player asking is the player being looked at.")
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

    @Operation(summary = "Get a player's name history", description = "The names a player has gone by before this one, most "
            + "recent first. We pick these up whenever their profile gets refreshed from BeatLeader or ScoreSaber.")
    @GetMapping("/{userId}/name-history")
    public ResponseEntity<List<NameHistoryResponse>> getNameHistory(@PathVariable Long userId) {
        List<NameHistoryResponse> history = userService.getNameHistory(userId).stream()
                .map(h -> new NameHistoryResponse(h.getName(), h.getChangedAt()))
                .toList();
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Get a player's pinned scores", description = "The scores a player has chosen to show off on their "
            + "profile, in the order they want them displayed. Four at most, eight for supporters.")
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

    @Operation(summary = "Get a player's pinned milestones", description = "The milestones a player has chosen to show off on "
            + "their profile, in the order they want them displayed. Four at most, eight for supporters.")
    @GetMapping("/{userId}/pinned-milestones")
    public ResponseEntity<List<UserMilestoneProgressResponse>> getPinnedMilestones(@PathVariable Long userId) {
        return ResponseEntity.ok(milestoneService.findPinnedByUser(userId));
    }

    @Operation(summary = "Get a player's stats in every category", description = "One call that gives you the current stats for "
            + "all categories at once, plus the XP breakdown and their clan side: the clan they are in, their rank there, "
            + "and their war record across every clan they have fought for. Reach for this rather than looping the single "
            + "category route.")
    @GetMapping("/{userId}/statistics/all")
    public ResponseEntity<UserAllStatisticsResponse> getAllUserStatistics(@PathVariable Long userId) {
        return ResponseEntity.ok(statisticsService.findAllByUser(userId));
    }

    @Operation(summary = "Get a player's stats in one category", description = "Where a player currently stands in a single "
            + "category, so their AP, rank, country rank, average accuracy and ranked play count. Pass the category code, one of "
            + "true_acc, standard_acc, tech_acc and so on. Leave it off and you get overall.")
    @GetMapping("/{userId}/statistics")
    public ResponseEntity<UserCategoryStatisticsResponse> getUserStatistics(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category) {
        return ResponseEntity.ok(statisticsService.findByUserAndCategoryCode(userId, category));
    }

    @Operation(summary = "Get a player's stats over time", description = "Every version of a player's stats in a category across "
            + "a time range, oldest first, which is what you want for charting progress. Nothing is ever overwritten here, so "
            + "each entry is a real snapshot from the moment it changed. Unit is h for hours, d for days, w for weeks or mo for "
            + "months, and amount is how many of those to go back.")
    @GetMapping("/{userId}/statistics/historic")
    public ResponseEntity<List<UserCategoryStatisticsResponse>> getUserStatisticsHistoric(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(statisticsService.findHistoric(userId, category, amount, unit));
    }

    @Operation(summary = "Get a player's rank over time", description = "Daily snapshots of where a player sat in a category, "
            + "oldest first. This is the lighter option if all you want to draw is the rank line, since the full stats history "
            + "carries a lot more with it. Same unit and amount parameters as the other history routes.")
    @GetMapping("/{userId}/ranking-history")
    public ResponseEntity<List<RankingHistoryResponse>> getUserRankingHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(statisticsService.findRankingHistory(userId, category, amount, unit));
    }

    @Operation(summary = "Get a player's last 24 hours", description = "How much a player has moved since yesterday. We take "
            + "their newest stats and subtract the last set from before the 24 hour mark, so you get the AP, rank and accuracy "
            + "change without working it out yourself. You get a 204 with nothing in it when there is no baseline to compare "
            + "against, which happens for a new player or one who was not active before then.")
    @GetMapping("/{userId}/stats-diff")
    public ResponseEntity<StatsDiffResponse> getStatsDiff(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category) {
        Optional<StatsDiffResponse> diff = statisticsService.computeStatsDiff(userId, category);
        return diff.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Get a player's score history on one difficulty", description = "Every score a player has had on a "
            + "single difficulty across a time range, oldest first. Because scores are versioned rather than overwritten, this "
            + "shows you each improvement as its own entry rather than only the current best.")
    @GetMapping("/{userId}/scores/historic")
    public ResponseEntity<List<ScoreResponse>> getUserScoresHistoric(
            @PathVariable Long userId,
            @RequestParam UUID mapDifficultyId,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(scoreService.findHistoric(userId, mapDifficultyId, amount, unit));
    }

    @Operation(summary = "Get a player's score on one map", description = "A player's current score on a difficulty, looked up "
            + "by song hash rather than by our difficulty id, which is handy when you are working from a local file. Difficulty "
            + "is required and is one of EASY, NORMAL, HARD, EXPERT or EXPERT_PLUS. Characteristic defaults to Standard if you "
            + "leave it off.")
    @GetMapping("/{userId}/scores/by-hash/{songHash}")
    public ResponseEntity<ScoreResponse> getUserScoreBySongHash(
            @PathVariable Long userId,
            @PathVariable String songHash,
            @RequestParam Difficulty difficulty,
            @RequestParam(defaultValue = "Standard") String characteristic) {
        return ResponseEntity
                .ok(scoreService.findActiveByUserAndSongHash(userId, songHash, difficulty, characteristic));
    }

    @Operation(summary = "Get a player's scores", description = "A page of a player's current scores, best AP first. You can "
            + "narrow it to one category, passing either the UUID or the code, and search by song name. Only the active score "
            + "per difficulty shows up here, so if you want the older attempts have a look at the score history route. The "
            + "exceptions are maxStreak115, playCount and lastPlayedAt, which count every attempt rather than just the score on "
            + "the row. Sort by lastPlayedAt to find the maps you have not touched in the longest.")
    @GetMapping("/{userId}/scores")
    public ResponseEntity<Page<ScoreResponse>> getUserScores(
            @PathVariable Long userId,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "ap", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity
                .ok(scoreService.findByUser(userId, categoryService.resolveId(categoryId), search, pageable));
    }

    @Operation(summary = "Get all of a player's scores at once", description = "Every current score a player has, in one flat "
            + "list with no paging, best AP first. Each entry is trimmed down to the fields you need to identify the map and "
            + "show the score, which keeps the payload sensible even for players with thousands of them. This is what the game "
            + "plugin uses to fill its cache in a single request, and it is the right choice if you want the whole set rather "
            + "than a page of it.")
    @GetMapping("/{userId}/scores/all")
    public ResponseEntity<List<UserScoreSummaryResponse>> getAllUserScores(@PathVariable Long userId) {
        return ResponseEntity.ok(scoreService.findAllSummariesByUser(userId));
    }

    @Operation(summary = "Get a player's milestone progress", description = "A page of every milestone with how far this player "
            + "has got on each one, finished or not. If you only care about one side of that, the completed and uncompleted "
            + "routes below give you those directly as flat lists.")
    @GetMapping("/{userId}/milestones")
    public ResponseEntity<Page<UserMilestoneProgressResponse>> getUserMilestones(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findUserProgress(userId, pageable));
    }

    @Operation(summary = "Get the milestones a player has finished", description = "Just the ones they have completed, as a flat "
            + "list rather than a page, each with when they got it.")
    @GetMapping("/{userId}/milestones/completed")
    public ResponseEntity<List<UserMilestoneProgressResponse>> getUserCompletedMilestones(
            @PathVariable Long userId) {
        return ResponseEntity.ok(milestoneService.findCompletedByUser(userId));
    }

    @Operation(summary = "Get the milestones a player still has left", description = "The other side of the completed list, so "
            + "everything they have not finished yet with their current progress toward each.")
    @GetMapping("/{userId}/milestones/uncompleted")
    public ResponseEntity<List<UserMilestoneProgressResponse>> getUserUncompletedMilestones(
            @PathVariable Long userId) {
        return ResponseEntity.ok(milestoneService.findUncompletedByUser(userId));
    }

    @Operation(summary = "Get a player's level and XP", description = "What level a player is on, how much XP they have "
            + "altogether, and how far they are through the current level. XP comes from scores, milestones and campaigns, and "
            + "the thresholds between levels are configurable, so work them out from here rather than assuming a formula.")
    @GetMapping("/{userId}/level")
    public ResponseEntity<LevelResponse> getUserLevel(@PathVariable Long userId) {
        var totalXp = userService.getTotalXp(userId);
        return ResponseEntity.ok(levelService.calculateLevel(totalXp));
    }

    @Operation(summary = "Get the maps a player has not played", description = "Ranked difficulties this player has no score on "
            + "yet, which is the basis of the missing maps playlist. Takes the same filters as the difficulty list, so you can "
            + "scope it to one category or a complexity range.")
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

    @Operation(summary = "Get the maps where a player is above an AP threshold", description = "Every difficulty where this "
            + "player already has a score worth at least apMin, as a flat list. Useful for building a practice set around the "
            + "level someone is actually at. You can scope it to one category too.")
    @GetMapping("/{userId}/maps-above-ap")
    public ResponseEntity<List<PublicMapDifficultyResponse>> getMapsAboveAp(
            @PathVariable Long userId,
            @RequestParam Double apMin,
            @RequestParam(required = false) String categoryId) {
        return ResponseEntity.ok(mapService.findDifficultiesWithUserScoreAbovePublic(userId, apMin,
                categoryService.resolveId(categoryId)));
    }

    @Operation(summary = "List a player's campaigns", description = "Campaigns this player has started, with how far through "
            + "each one they are and everything the campaign list gives you. Filters and sorting match the campaign list, and "
            + "progressStatus narrows it to the ones they are still playing or have already finished. Signing in is optional "
            + "and only changes whether your own vote comes back on each campaign.")
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

    @Operation(summary = "Get a player's progress in a campaign", description = "How far a given player has got through one "
            + "campaign, node by node, including which are unlocked and what their best is on each. If you want this for "
            + "yourself rather than someone else, the campaign routes have a me variant that saves you looking up your own id.")
    @GetMapping("/{userId}/campaigns/{campaignId}")
    public ResponseEntity<CampaignProgressResponse> getUserCampaignProgress(
            @PathVariable Long userId,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.getUserProgress(userId, campaignId));
    }
}
