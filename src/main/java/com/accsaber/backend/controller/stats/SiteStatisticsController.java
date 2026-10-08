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
import com.accsaber.backend.model.dto.response.statistics.BiggestTraderResponse;
import com.accsaber.backend.model.dto.response.statistics.CollectionCompletionResponse;
import com.accsaber.backend.model.dto.response.statistics.DistributionEntryResponse;
import com.accsaber.backend.model.dto.response.statistics.EssenceEarnedResponse;
import com.accsaber.backend.model.dto.response.statistics.FirstEditionHolderResponse;
import com.accsaber.backend.model.dto.response.statistics.FirstEditionsResponse;
import com.accsaber.backend.model.dto.response.statistics.InventoryValueResponse;
import com.accsaber.backend.model.dto.response.statistics.ItemScarcityResponse;
import com.accsaber.backend.model.dto.response.statistics.MapAvgApResponse;
import com.accsaber.backend.model.dto.response.statistics.MapRetryResponse;
import com.accsaber.backend.model.dto.response.statistics.MilestoneCollectorResponse;
import com.accsaber.backend.model.dto.response.statistics.MostCratesOpenedResponse;
import com.accsaber.backend.model.dto.response.statistics.MostItemsResponse;
import com.accsaber.backend.model.dto.response.statistics.RarestUnboxedResponse;
import com.accsaber.backend.model.dto.response.statistics.TimeSeriesPointResponse;
import com.accsaber.backend.model.dto.response.statistics.UserImprovementsResponse;
import com.accsaber.backend.model.dto.response.statistics.UserMapImprovementsResponse;
import com.accsaber.backend.service.infra.CategoryService;
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
    private final CategoryService categoryService;

    @Operation(summary = "Longest 115 streaks", description = "BeatLeader scores only. Every attempt counts. One "
            + "entry per player per difficulty. Filter by category or country.")
    @GetMapping("/leaderboards/streaks")
    public ResponseEntity<Page<ScoreResponse>> getTopStreaks(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity
                .ok(siteStatisticsService.getTopStreaks(categoryService.resolveId(categoryId), country, pageable));
    }

    @Operation(summary = "Top AP scores", description = "Filter by category or country.")
    @GetMapping("/leaderboards/max-ap")
    public ResponseEntity<Page<ScoreResponse>> getTopByAp(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity
                .ok(siteStatisticsService.getTopByAp(categoryService.resolveId(categoryId), country, pageable));
    }

    @Operation(summary = "Highest average AP maps", description = "Average weighted AP per difficulty. Set "
            + "a minimum score count to hide barely played maps.")
    @GetMapping("/leaderboards/highest-avg-ap")
    public ResponseEntity<Page<MapAvgApResponse>> getHighestAvgAp(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "5") int minScores,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getHighestAvgAp(categoryService.resolveId(categoryId), country,
                minScores, pageable));
    }

    @Operation(summary = "Most retried maps", description = "Counts superseded scores.")
    @GetMapping("/leaderboards/most-retried")
    public ResponseEntity<Page<MapRetryResponse>> getMostRetriedMaps(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity
                .ok(siteStatisticsService.getMostRetriedMaps(categoryService.resolveId(categoryId), country, pageable));
    }

    @Operation(summary = "Top self-improvers", description = "Ranked by how many times someone beat their "
            + "own score.")
    @GetMapping("/leaderboards/most-improvements")
    public ResponseEntity<Page<UserImprovementsResponse>> getMostImprovements(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                siteStatisticsService.getMostImprovements(categoryService.resolveId(categoryId), country, pageable));
    }

    @Operation(summary = "Most PBs on one map", description = "Ranked by the most PBs anyone set on a single "
            + "difficulty.")
    @GetMapping("/leaderboards/most-map-improvements")
    public ResponseEntity<Page<UserMapImprovementsResponse>> getMostMapImprovements(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                siteStatisticsService.getMostMapImprovements(categoryService.resolveId(categoryId), country, pageable));
    }

    @Operation(summary = "Milestone collectors")
    @GetMapping("/leaderboards/milestone-collectors")
    public ResponseEntity<Page<MilestoneCollectorResponse>> getMilestoneCollectors(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMilestoneCollectors(country, pageable));
    }

    @Operation(summary = "Biggest collections", description = "Tradeable items only. Filter by item type, modifier "
            + "or country.")
    @GetMapping("/leaderboards/most-items")
    public ResponseEntity<Page<MostItemsResponse>> getMostItems(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String modifier,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostItems(type, modifier, country, pageable));
    }

    @Operation(summary = "Most crates opened", description = "Pass a crate item ID to count one kind of crate.")
    @GetMapping("/leaderboards/most-crates-opened")
    public ResponseEntity<Page<MostCratesOpenedResponse>> getMostCratesOpened(
            @RequestParam(required = false) UUID crateId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostCratesOpened(crateId, country, pageable));
    }

    @Operation(summary = "Luckiest crate pulls", description = "Crate drops ranked by modifier count, then rarity.")
    @GetMapping("/leaderboards/rarest-unboxed")
    public ResponseEntity<Page<RarestUnboxedResponse>> getRarestUnboxed(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getRarestUnboxed(country, pageable));
    }

    @Operation(summary = "Richest inventories", description = "Disintegrate value of tradeable items plus "
            + "essence on hand.")
    @GetMapping("/leaderboards/most-valuable-inventory")
    public ResponseEntity<Page<InventoryValueResponse>> getMostValuableInventory(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostValuableInventory(country, pageable));
    }

    @Operation(summary = "Most first editions", description = "Counts serial number one items.")
    @GetMapping("/leaderboards/first-editions")
    public ResponseEntity<Page<FirstEditionsResponse>> getFirstEditions(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getFirstEditions(country, pageable));
    }

    @Operation(summary = "First edition owners", description = "Who holds serial number one of each "
            + "tradeable item.")
    @GetMapping("/leaderboards/first-edition-holders")
    public ResponseEntity<Page<FirstEditionHolderResponse>> getFirstEditionHolders(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getFirstEditionHolders(country, pageable));
    }

    @Operation(summary = "Most complete collections", description = "Share of the tradeable catalogue owned, as a "
            + "percentage.")
    @GetMapping("/leaderboards/most-complete-collection")
    public ResponseEntity<Page<CollectionCompletionResponse>> getMostCompleteCollection(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostCompleteCollection(country, pageable));
    }

    @Operation(summary = "Rarest items", description = "Has copy count and distinct owner count.")
    @GetMapping("/leaderboards/rarest-items")
    public ResponseEntity<Page<ItemScarcityResponse>> getItemScarcity(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getItemScarcity(pageable));
    }

    @Operation(summary = "Busiest traders", description = "Completed trades only.")
    @GetMapping("/leaderboards/biggest-traders")
    public ResponseEntity<Page<BiggestTraderResponse>> getBiggestTraders(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getBiggestTraders(country, pageable));
    }

    @Operation(summary = "Most essence earned", description = "Lifetime essence from disintegrating. It is not the "
            + "current balance.")
    @GetMapping("/leaderboards/most-essence-earned")
    public ResponseEntity<Page<EssenceEarnedResponse>> getMostEssenceEarned(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostEssenceEarned(country, pageable));
    }

    @Operation(summary = "New players per day", description = "unit is h, d, w or mo, amount is how many back. Over "
            + "65 days rolls up to weekly. Filter by country.")
    @GetMapping("/charts/new-players-per-day")
    public ResponseEntity<List<TimeSeriesPointResponse>> getNewPlayersPerDay(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getNewPlayersPerDay(amount, unit, country));
    }

    @Operation(summary = "Scores per day", description = "unit is h, d, w or mo, amount is how many back. Over 65 "
            + "days rolls up to weekly. Filter by country.")
    @GetMapping("/charts/scores-per-day")
    public ResponseEntity<List<TimeSeriesPointResponse>> getScoresPerDay(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getScoresPerDay(amount, unit, country));
    }

    @Operation(summary = "Accounts over time", description = "Running total of active accounts. unit is h, d, w or "
            + "mo, amount is how many back. Over 65 days rolls up to weekly.")
    @GetMapping("/charts/cumulative-accounts")
    public ResponseEntity<List<TimeSeriesPointResponse>> getCumulativeAccounts(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getCumulativeAccounts(amount, unit, country));
    }

    @Operation(summary = "Scores over time", description = "Running total of all scores. unit is h, d, w or mo, "
            + "amount is how many back. Over 65 days rolls up to weekly.")
    @GetMapping("/charts/cumulative-scores")
    public ResponseEntity<List<TimeSeriesPointResponse>> getCumulativeScores(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getCumulativeScores(amount, unit, country));
    }

    @Operation(summary = "Scores by category")
    @GetMapping("/charts/scores-per-category")
    public ResponseEntity<List<DistributionEntryResponse>> getScoresPerCategory(
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getScoresPerCategory(country));
    }

    @Operation(summary = "Players by headset", description = "Uses the headset from each player's latest score.")
    @GetMapping("/charts/players-by-hmd")
    public ResponseEntity<List<DistributionEntryResponse>> getPlayersByHmd(
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getPlayersByHmd(country));
    }

    @Operation(summary = "Players by country", description = "Active players only.")
    @GetMapping("/charts/players-per-country")
    public ResponseEntity<List<DistributionEntryResponse>> getPlayersPerCountry() {
        return ResponseEntity.ok(siteStatisticsService.getPlayersPerCountry());
    }
}
