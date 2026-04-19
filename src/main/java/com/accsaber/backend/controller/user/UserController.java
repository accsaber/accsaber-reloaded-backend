package com.accsaber.backend.controller.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.dto.response.milestone.UserMilestoneProgressResponse;
import com.accsaber.backend.model.dto.response.player.NameHistoryResponse;
import com.accsaber.backend.model.dto.response.player.RankingHistoryResponse;
import com.accsaber.backend.model.dto.response.player.StatsDiffResponse;
import com.accsaber.backend.model.dto.response.player.UserAllStatisticsResponse;
import com.accsaber.backend.model.dto.response.player.UserCategoryStatisticsResponse;
import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.service.campaign.CampaignService;
import com.accsaber.backend.service.map.MapService;
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
    private final MapService mapService;
    private final MilestoneService milestoneService;
    private final LevelService levelService;
    private final CampaignService campaignService;

    @Operation(summary = "Get user profile", description = "Returns a player profile by user ID. Optionally include all category statistics.")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean statistics) {
        UserResponse user = userService.findByUserId(userId);
        if (statistics) {
            user = user.withStatistics(statisticsService.findCategoryStatsByUser(userId));
        }
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Get user name history", description = "Returns a player's previous names, most recent first")
    @GetMapping("/{userId}/name-history")
    public ResponseEntity<List<NameHistoryResponse>> getNameHistory(@PathVariable Long userId) {
        List<NameHistoryResponse> history = userService.getNameHistory(userId).stream()
                .map(h -> new NameHistoryResponse(h.getName(), h.getChangedAt()))
                .toList();
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Get all user statistics", description = "Returns all active category statistics plus XP breakdown for a player")
    @GetMapping("/{userId}/statistics/all")
    public ResponseEntity<UserAllStatisticsResponse> getAllUserStatistics(@PathVariable Long userId) {
        return ResponseEntity.ok(statisticsService.findAllByUser(userId));
    }

    @Operation(summary = "Get user category statistics", description = "Returns active category statistics for a player by category code (tech_acc, standard_acc, true_acc) (defaults to 'overall')")
    @GetMapping("/{userId}/statistics")
    public ResponseEntity<UserCategoryStatisticsResponse> getUserStatistics(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category) {
        return ResponseEntity.ok(statisticsService.findByUserAndCategoryCode(userId, category));
    }

    @Operation(summary = "Get historic user category statistics", description = "Returns all versioned statistics for a player over a time range, sorted by time ascending. "
            + "Units: h (hours), d (days), w (weeks), mo (months)")
    @GetMapping("/{userId}/statistics/historic")
    public ResponseEntity<List<UserCategoryStatisticsResponse>> getUserStatisticsHistoric(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(statisticsService.findHistoric(userId, category, amount, unit));
    }

    @Operation(summary = "Get user ranking history", description = "Returns daily ranking snapshots for a player in a category over a time range, sorted by time ascending. "
            + "Units: h (hours), d (days), w (weeks), mo (months)")
    @GetMapping("/{userId}/ranking-history")
    public ResponseEntity<List<RankingHistoryResponse>> getUserRankingHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(statisticsService.findRankingHistory(userId, category, amount, unit));
    }

    @Operation(summary = "Get user stats diff", description = "Returns the difference between the most recent statistics and the last statistics before 24h ago. "
            + "Returns 204 No Content if no baseline exists (new player or no activity before 24h ago)")
    @GetMapping("/{userId}/stats-diff")
    public ResponseEntity<StatsDiffResponse> getStatsDiff(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "overall") String category) {
        Optional<StatsDiffResponse> diff = statisticsService.computeStatsDiff(userId, category);
        return diff.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Get historic user scores", description = "Returns all versioned scores for a player on a specific map difficulty over a time range, sorted by time ascending. "
            + "Units: h (hours), d (days), w (weeks), mo (months)")
    @GetMapping("/{userId}/scores/historic")
    public ResponseEntity<List<ScoreResponse>> getUserScoresHistoric(
            @PathVariable Long userId,
            @RequestParam UUID mapDifficultyId,
            @RequestParam(defaultValue = "7") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(scoreService.findHistoric(userId, mapDifficultyId, amount, unit));
    }

    @Operation(summary = "Get user score by song hash", description = "Returns a player's active score on a specific map difficulty, looked up by song hash, difficulty (EASY, NORMAL, HARD, EXPERT, EXPERT_PLUS) and characteristic (defaults to Standard)")
    @GetMapping("/{userId}/scores/by-hash/{songHash}")
    public ResponseEntity<ScoreResponse> getUserScoreBySongHash(
            @PathVariable Long userId,
            @PathVariable String songHash,
            @RequestParam Difficulty difficulty,
            @RequestParam(defaultValue = "Standard") String characteristic) {
        return ResponseEntity
                .ok(scoreService.findActiveByUserAndSongHash(userId, songHash, difficulty, characteristic));
    }

    @Operation(summary = "Get user scores", description = "Paginated list of a player's active scores, optionally filtered by category and/or song name")
    @GetMapping("/{userId}/scores")
    public ResponseEntity<Page<ScoreResponse>> getUserScores(
            @PathVariable Long userId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "ap", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(scoreService.findByUser(userId, categoryId, search, pageable));
    }

    @Operation(summary = "Get user milestone progress")
    @GetMapping("/{userId}/milestones")
    public ResponseEntity<Page<UserMilestoneProgressResponse>> getUserMilestones(
            @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findUserProgress(userId, pageable));
    }

    @Operation(summary = "Get user completed milestones")
    @GetMapping("/{userId}/milestones/completed")
    public ResponseEntity<List<UserMilestoneProgressResponse>> getUserCompletedMilestones(
            @PathVariable Long userId) {
        return ResponseEntity.ok(milestoneService.findCompletedByUser(userId));
    }

    @Operation(summary = "Get user level and XP")
    @GetMapping("/{userId}/level")
    public ResponseEntity<LevelResponse> getUserLevel(@PathVariable Long userId) {
        var totalXp = userService.getTotalXp(userId);
        return ResponseEntity.ok(levelService.calculateLevel(totalXp));
    }

    @Operation(summary = "Get unplayed maps for a user", description = "Paginated list of ranked difficulties the user has no active score on, with the same filters as the maps/difficulties endpoint")
    @GetMapping("/{userId}/missing-maps")
    public ResponseEntity<Page<PublicMapDifficultyResponse>> getMissingMaps(
            @PathVariable Long userId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) MapDifficultyStatus status,
            @RequestParam(required = false) BigDecimal complexityMin,
            @RequestParam(required = false) BigDecimal complexityMax,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "rankedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(mapService.findDifficultiesPublic(categoryId, status, complexityMin, complexityMax,
                search, userId, pageable));
    }

    @Operation(summary = "Get user progress in a campaign")
    @GetMapping("/{userId}/campaigns/{campaignId}")
    public ResponseEntity<CampaignProgressResponse> getUserCampaignProgress(
            @PathVariable Long userId,
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(campaignService.getUserProgress(userId, campaignId));
    }
}
