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

    @Operation(summary = "Top 115 streaks", description = "Scores ranked by highest 115 note streak. Optional category (UUID or code) and country filters.")
    @GetMapping("/leaderboards/streaks")
    public ResponseEntity<Page<ScoreResponse>> getTopStreaks(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity
                .ok(siteStatisticsService.getTopStreaks(categoryService.resolveId(categoryId), country, pageable));
    }

    @Operation(summary = "Top scores by AP", description = "Scores ranked by highest AP. Optional category (UUID or code) and country filters.")
    @GetMapping("/leaderboards/max-ap")
    public ResponseEntity<Page<ScoreResponse>> getTopByAp(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity
                .ok(siteStatisticsService.getTopByAp(categoryService.resolveId(categoryId), country, pageable));
    }

    @Operation(summary = "Maps with highest average weighted AP", description = "Map difficulties ranked by average weighted AP across all scores. Optional category (UUID or code) and country filters and minimum score threshold.")
    @GetMapping("/leaderboards/highest-avg-ap")
    public ResponseEntity<Page<MapAvgApResponse>> getHighestAvgAp(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "5") int minScores,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getHighestAvgAp(categoryService.resolveId(categoryId), country,
                minScores, pageable));
    }

    @Operation(summary = "Most retried maps", description = "Map difficulties ranked by number of superseded scores (improvements). Optional category (UUID or code) and country filters.")
    @GetMapping("/leaderboards/most-retried")
    public ResponseEntity<Page<MapRetryResponse>> getMostRetriedMaps(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity
                .ok(siteStatisticsService.getMostRetriedMaps(categoryService.resolveId(categoryId), country, pageable));
    }

    @Operation(summary = "Users with most improvements", description = "Users ranked by total number of superseded scores across all maps. Optional category (UUID or code) and country filters.")
    @GetMapping("/leaderboards/most-improvements")
    public ResponseEntity<Page<UserImprovementsResponse>> getMostImprovements(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                siteStatisticsService.getMostImprovements(categoryService.resolveId(categoryId), country, pageable));
    }

    @Operation(summary = "Users with most improvements on a single map", description = "Users ranked by most superseded scores on any single map difficulty. Optional category (UUID or code) and country filters.")
    @GetMapping("/leaderboards/most-map-improvements")
    public ResponseEntity<Page<UserMapImprovementsResponse>> getMostMapImprovements(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                siteStatisticsService.getMostMapImprovements(categoryService.resolveId(categoryId), country, pageable));
    }

    @Operation(summary = "Milestone collectors", description = "Users ranked by number of completed milestones. Optional country filter.")
    @GetMapping("/leaderboards/milestone-collectors")
    public ResponseEntity<Page<MilestoneCollectorResponse>> getMilestoneCollectors(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMilestoneCollectors(country, pageable));
    }

    @Operation(summary = "Most items owned", description = "Users ranked by total quantity of tradeable items owned. Untradeable items never count. Optional item type key, modifier key, and country filters.")
    @GetMapping("/leaderboards/most-items")
    public ResponseEntity<Page<MostItemsResponse>> getMostItems(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String modifier,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostItems(type, modifier, country, pageable));
    }

    @Operation(summary = "Most crates opened", description = "Users ranked by number of crates opened. Optional crate item id and country filters.")
    @GetMapping("/leaderboards/most-crates-opened")
    public ResponseEntity<Page<MostCratesOpenedResponse>> getMostCratesOpened(
            @RequestParam(required = false) UUID crateId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostCratesOpened(crateId, country, pageable));
    }

    @Operation(summary = "Rarest items unboxed", description = "Tradeable item instances pulled from crates, ranked by modifier count then rarity. Optional country filter.")
    @GetMapping("/leaderboards/rarest-unboxed")
    public ResponseEntity<Page<RarestUnboxedResponse>> getRarestUnboxed(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getRarestUnboxed(country, pageable));
    }

    @Operation(summary = "Most valuable inventory", description = "Users ranked by liquidation value of tradeable items plus item essence balance. Optional country filter.")
    @GetMapping("/leaderboards/most-valuable-inventory")
    public ResponseEntity<Page<InventoryValueResponse>> getMostValuableInventory(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostValuableInventory(country, pageable));
    }

    @Operation(summary = "First editions", description = "Users ranked by number of serial #1 tradeable items owned. Optional country filter.")
    @GetMapping("/leaderboards/first-editions")
    public ResponseEntity<Page<FirstEditionsResponse>> getFirstEditions(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getFirstEditions(country, pageable));
    }

    @Operation(summary = "First edition holders", description = "For each tradeable item, the owner of its serial #1. Optional country filter (on the holder).")
    @GetMapping("/leaderboards/first-edition-holders")
    public ResponseEntity<Page<FirstEditionHolderResponse>> getFirstEditionHolders(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getFirstEditionHolders(country, pageable));
    }

    @Operation(summary = "Most complete collection", description = "Users ranked by percentage of the tradeable catalog owned. Optional country filter.")
    @GetMapping("/leaderboards/most-complete-collection")
    public ResponseEntity<Page<CollectionCompletionResponse>> getMostCompleteCollection(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostCompleteCollection(country, pageable));
    }

    @Operation(summary = "Item scarcity", description = "Tradeable catalog items ranked by fewest distinct owners.")
    @GetMapping("/leaderboards/rarest-items")
    public ResponseEntity<Page<ItemScarcityResponse>> getItemScarcity(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getItemScarcity(pageable));
    }

    @Operation(summary = "Biggest traders", description = "Users ranked by number of accepted trades participated in. Optional country filter.")
    @GetMapping("/leaderboards/biggest-traders")
    public ResponseEntity<Page<BiggestTraderResponse>> getBiggestTraders(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getBiggestTraders(country, pageable));
    }

    @Operation(summary = "Most essence earned", description = "Users ranked by total item essence gained from disintegrating tradeable items. Optional country filter.")
    @GetMapping("/leaderboards/most-essence-earned")
    public ResponseEntity<Page<EssenceEarnedResponse>> getMostEssenceEarned(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(siteStatisticsService.getMostEssenceEarned(country, pageable));
    }

    @Operation(summary = "New players per day", description = "Count of new player registrations over a time range. "
            + "Units: h (hours), d (days), w (weeks), mo (months). Ranges over 65 days are downsampled to weekly. Optional country filter.")
    @GetMapping("/charts/new-players-per-day")
    public ResponseEntity<List<TimeSeriesPointResponse>> getNewPlayersPerDay(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getNewPlayersPerDay(amount, unit, country));
    }

    @Operation(summary = "Scores per day", description = "Count of score submissions over a time range. "
            + "Units: h (hours), d (days), w (weeks), mo (months). Ranges over 65 days are downsampled to weekly. Optional country filter.")
    @GetMapping("/charts/scores-per-day")
    public ResponseEntity<List<TimeSeriesPointResponse>> getScoresPerDay(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getScoresPerDay(amount, unit, country));
    }

    @Operation(summary = "Cumulative active accounts", description = "Running total of active accounts over a time range. "
            + "Units: h (hours), d (days), w (weeks), mo (months). Ranges over 65 days are downsampled to weekly. Optional country filter.")
    @GetMapping("/charts/cumulative-accounts")
    public ResponseEntity<List<TimeSeriesPointResponse>> getCumulativeAccounts(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getCumulativeAccounts(amount, unit, country));
    }

    @Operation(summary = "Cumulative tracked scores", description = "Running total of tracked scores over a time range. "
            + "Units: h (hours), d (days), w (weeks), mo (months). Ranges over 65 days are downsampled to weekly. Optional country filter.")
    @GetMapping("/charts/cumulative-scores")
    public ResponseEntity<List<TimeSeriesPointResponse>> getCumulativeScores(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getCumulativeScores(amount, unit, country));
    }

    @Operation(summary = "Scores per category", description = "Distribution of active scores across categories. Optional country filter.")
    @GetMapping("/charts/scores-per-category")
    public ResponseEntity<List<DistributionEntryResponse>> getScoresPerCategory(
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getScoresPerCategory(country));
    }

    @Operation(summary = "Players by HMD", description = "Distribution of players by headset model (from most recent score). Optional country filter.")
    @GetMapping("/charts/players-by-hmd")
    public ResponseEntity<List<DistributionEntryResponse>> getPlayersByHmd(
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(siteStatisticsService.getPlayersByHmd(country));
    }

    @Operation(summary = "Players per country", description = "Distribution of active players by country.")
    @GetMapping("/charts/players-per-country")
    public ResponseEntity<List<DistributionEntryResponse>> getPlayersPerCountry() {
        return ResponseEntity.ok(siteStatisticsService.getPlayersPerCountry());
    }
}
