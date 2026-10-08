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

import com.accsaber.backend.model.dto.response.statistics.CampaignCompletorResponse;
import com.accsaber.backend.model.dto.response.statistics.CampaignCreatorResponse;
import com.accsaber.backend.model.dto.response.statistics.CampaignFunnelResponse;
import com.accsaber.backend.model.dto.response.statistics.CampaignNodeDifficultyResponse;
import com.accsaber.backend.model.dto.response.statistics.TimeSeriesPointResponse;
import com.accsaber.backend.service.stats.CampaignStatisticsService;
import com.accsaber.backend.service.stats.CampaignStatsFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/statistics/campaigns")
@RequiredArgsConstructor
@Tag(name = "Site Statistics")
public class CampaignStatisticsController {

    private final CampaignStatisticsService campaignStatisticsService;

    @Operation(summary = "Campaign funnel", description = "Started, ongoing, finished and dropped per campaign, plus "
            + "median days to finish. status matches any of the ones you pass. minParticipants hides tiny "
            + "campaigns.")
    @GetMapping("/funnel")
    public ResponseEntity<Page<CampaignFunnelResponse>> getFunnel(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "3") int minParticipants,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(campaignStatisticsService.getFunnel(
                new CampaignStatsFilter(status, country, minParticipants), pageable));
    }

    @Operation(summary = "Where players get stuck", description = "Unlocks against clears per node, hardest first. "
            + "Barriers included.")
    @GetMapping("/hardest-nodes")
    public ResponseEntity<List<CampaignNodeDifficultyResponse>> getHardestNodes(
            @RequestParam UUID campaignId,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(campaignStatisticsService.getNodeDifficulty(campaignId, country));
    }

    @Operation(summary = "Campaigns started per day", description = "unit is h, d, w or mo. amount is how many to go "
            + "back.")
    @GetMapping("/charts/starts-per-day")
    public ResponseEntity<List<TimeSeriesPointResponse>> getStartsPerDay(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(campaignStatisticsService.getStartsPerDay(amount, unit,
                new CampaignStatsFilter(status, country, 0)));
    }

    @Operation(summary = "Campaigns finished per day", description = "unit is h, d, w or mo.")
    @GetMapping("/charts/completions-per-day")
    public ResponseEntity<List<TimeSeriesPointResponse>> getCompletionsPerDay(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(campaignStatisticsService.getCompletionsPerDay(amount, unit,
                new CampaignStatsFilter(status, country, 0)));
    }

    @Operation(summary = "Most campaigns completed", description = "Also shows cleared nodes and campaign XP.")
    @GetMapping("/leaderboards/most-completed")
    public ResponseEntity<Page<CampaignCompletorResponse>> getMostCompleted(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(campaignStatisticsService.getMostCompleted(
                new CampaignStatsFilter(status, country, 0), pageable));
    }

    @Operation(summary = "Top campaign creators", description = "Ranked by players and finishers. Drafts and "
            + "official campaigns do not count.")
    @GetMapping("/leaderboards/top-creators")
    public ResponseEntity<Page<CampaignCreatorResponse>> getTopCreators(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(campaignStatisticsService.getTopCreators(
                new CampaignStatsFilter(status, country, 0), pageable));
    }
}
