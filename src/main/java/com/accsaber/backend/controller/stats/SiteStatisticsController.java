package com.accsaber.backend.controller.stats;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.dto.response.statistics.DistributionEntryResponse;
import com.accsaber.backend.model.dto.response.statistics.MapAvgApResponse;
import com.accsaber.backend.model.dto.response.statistics.MapRetryResponse;
import com.accsaber.backend.model.dto.response.statistics.MilestoneCollectorResponse;
import com.accsaber.backend.model.dto.response.statistics.TimeSeriesPointResponse;
import com.accsaber.backend.model.dto.response.statistics.UserImprovementsResponse;
import com.accsaber.backend.model.dto.response.statistics.UserMapImprovementsResponse;
import com.accsaber.backend.service.stats.SiteStatisticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/statistics")
@RequiredArgsConstructor
@Tag(name = "Site Statistics")
public class SiteStatisticsController {

    private final SiteStatisticsService siteStatisticsService;

    @Operation(summary = "Top 115 streaks", description = "Scores ranked by highest 115 note streak. Optional category filter.")
    @GetMapping("/leaderboards/streaks")
    public ResponseEntity<Page<ScoreResponse>> getTopStreaks(
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getTopStreaks(categoryId, pageable));
    }

    @Operation(summary = "Top scores by AP", description = "Scores ranked by highest AP. Optional category filter.")
    @GetMapping("/leaderboards/max-ap")
    public ResponseEntity<Page<ScoreResponse>> getTopByAp(
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getTopByAp(categoryId, pageable));
    }

    @Operation(summary = "Maps with highest average weighted AP", description = "Map difficulties ranked by average weighted AP across all scores. Optional category filter and minimum score threshold.")
    @GetMapping("/leaderboards/highest-avg-ap")
    public ResponseEntity<Page<MapAvgApResponse>> getHighestAvgAp(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "5") int minScores,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getHighestAvgAp(categoryId, minScores, pageable));
    }

    @Operation(summary = "Most retried maps", description = "Map difficulties ranked by number of superseded scores (improvements). Optional category filter.")
    @GetMapping("/leaderboards/most-retried")
    public ResponseEntity<Page<MapRetryResponse>> getMostRetriedMaps(
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostRetriedMaps(categoryId, pageable));
    }

    @Operation(summary = "Users with most improvements", description = "Users ranked by total number of superseded scores across all maps. Optional category filter.")
    @GetMapping("/leaderboards/most-improvements")
    public ResponseEntity<Page<UserImprovementsResponse>> getMostImprovements(
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostImprovements(categoryId, pageable));
    }

    @Operation(summary = "Users with most improvements on a single map", description = "Users ranked by most superseded scores on any single map difficulty. Optional category filter.")
    @GetMapping("/leaderboards/most-map-improvements")
    public ResponseEntity<Page<UserMapImprovementsResponse>> getMostMapImprovements(
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostMapImprovements(categoryId, pageable));
    }

    @Operation(summary = "Milestone collectors", description = "Users ranked by number of completed milestones.")
    @GetMapping("/leaderboards/milestone-collectors")
    public ResponseEntity<Page<MilestoneCollectorResponse>> getMilestoneCollectors(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMilestoneCollectors(pageable));
    }

    @Operation(summary = "New players per day", description = "Count of new player registrations over a time range. "
            + "Units: h (hours), d (days), w (weeks), mo (months). Ranges over 65 days are downsampled to weekly.")
    @GetMapping("/charts/new-players-per-day")
    public ResponseEntity<List<TimeSeriesPointResponse>> getNewPlayersPerDay(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(siteStatisticsService.getNewPlayersPerDay(amount, unit));
    }

    @Operation(summary = "Scores per day", description = "Count of score submissions over a time range. "
            + "Units: h (hours), d (days), w (weeks), mo (months). Ranges over 65 days are downsampled to weekly.")
    @GetMapping("/charts/scores-per-day")
    public ResponseEntity<List<TimeSeriesPointResponse>> getScoresPerDay(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(siteStatisticsService.getScoresPerDay(amount, unit));
    }

    @Operation(summary = "Cumulative active accounts", description = "Running total of active accounts over a time range. "
            + "Units: h (hours), d (days), w (weeks), mo (months). Ranges over 65 days are downsampled to weekly.")
    @GetMapping("/charts/cumulative-accounts")
    public ResponseEntity<List<TimeSeriesPointResponse>> getCumulativeAccounts(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(siteStatisticsService.getCumulativeAccounts(amount, unit));
    }

    @Operation(summary = "Cumulative tracked scores", description = "Running total of tracked scores over a time range. "
            + "Units: h (hours), d (days), w (weeks), mo (months). Ranges over 65 days are downsampled to weekly.")
    @GetMapping("/charts/cumulative-scores")
    public ResponseEntity<List<TimeSeriesPointResponse>> getCumulativeScores(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit) {
        return ResponseEntity.ok(siteStatisticsService.getCumulativeScores(amount, unit));
    }

    @Operation(summary = "Scores per category", description = "Distribution of active scores across categories.")
    @GetMapping("/charts/scores-per-category")
    public ResponseEntity<List<DistributionEntryResponse>> getScoresPerCategory() {
        return ResponseEntity.ok(siteStatisticsService.getScoresPerCategory());
    }

    @Operation(summary = "Players by HMD", description = "Distribution of players by headset model (from most recent score).")
    @GetMapping("/charts/players-by-hmd")
    public ResponseEntity<List<DistributionEntryResponse>> getPlayersByHmd() {
        return ResponseEntity.ok(siteStatisticsService.getPlayersByHmd());
    }

    @Operation(summary = "Players per country", description = "Distribution of active players by country.")
    @GetMapping("/charts/players-per-country")
    public ResponseEntity<List<DistributionEntryResponse>> getPlayersPerCountry() {
        return ResponseEntity.ok(siteStatisticsService.getPlayersPerCountry());
    }
}
