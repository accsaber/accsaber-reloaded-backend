package com.accsaber.backend.controller.stats;

import java.time.Instant;
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

import com.accsaber.backend.model.dto.response.statistics.DistributionEntryResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionCalibrationResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionCompletorResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionShortfallResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionXpResponse;
import com.accsaber.backend.model.dto.response.statistics.TimeSeriesPointResponse;
import com.accsaber.backend.model.entity.mission.MissionBand;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.service.infra.CategoryService;
import com.accsaber.backend.service.stats.MissionShortfallService;
import com.accsaber.backend.service.stats.MissionStatisticsService;
import com.accsaber.backend.service.stats.MissionStatsFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/statistics/missions")
@RequiredArgsConstructor
@Tag(name = "Site Statistics")
public class MissionStatisticsController {

    private final MissionStatisticsService missionStatisticsService;
    private final MissionShortfallService missionShortfallService;
    private final CategoryService categoryService;

    @Operation(summary = "Mission calibration table", description = "One row per template, band and skill tier. "
            + "Missions voided by staff do not count. Filters take several values and widen the result. skillMin "
            + "and skillMax cut on the raw skill value. minAssigned hides rare combos.")
    @GetMapping("/calibration")
    public ResponseEntity<Page<MissionCalibrationResponse>> getCalibration(
            @RequestParam(required = false) List<MissionPool> pool,
            @RequestParam(required = false) List<MissionType> type,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) List<String> categoryId,
            @RequestParam(required = false) List<MissionBand> band,
            @RequestParam(required = false) List<String> tier,
            @RequestParam(required = false) Double skillMin,
            @RequestParam(required = false) Double skillMax,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "1") int minAssigned,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(missionStatisticsService.getCalibration(
                filter(pool, type, templateId, categoryId, band, tier, skillMin, skillMax, country, from, to,
                        minAssigned),
                pageable));
    }

    @Operation(summary = "One mission by skill tier", description = "Completion rate for one template, from "
            + "new to elite.")
    @GetMapping("/calibration/by-tier")
    public ResponseEntity<List<MissionCalibrationResponse>> getByTier(
            @RequestParam UUID templateId,
            @RequestParam(required = false) List<MissionPool> pool,
            @RequestParam(required = false) List<String> categoryId,
            @RequestParam(required = false) List<MissionBand> band,
            @RequestParam(required = false) Double skillMin,
            @RequestParam(required = false) Double skillMax,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(missionStatisticsService.getByTier(templateId,
                filter(pool, null, null, categoryId, band, null, skillMin, skillMax, country, from, to, 1)));
    }

    @Operation(summary = "Mission XP payouts", description = "Total, average, median and 90th percentile XP "
            + "per template and band, plus its share of all mission XP.")
    @GetMapping("/xp")
    public ResponseEntity<Page<MissionXpResponse>> getXpPayouts(
            @RequestParam(required = false) List<MissionPool> pool,
            @RequestParam(required = false) List<MissionType> type,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) List<String> categoryId,
            @RequestParam(required = false) List<MissionBand> band,
            @RequestParam(required = false) List<String> tier,
            @RequestParam(required = false) Double skillMin,
            @RequestParam(required = false) Double skillMax,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(missionStatisticsService.getXpPayouts(
                filter(pool, type, templateId, categoryId, band, tier, skillMin, skillMax, country, from, to, 1),
                pageable));
    }

    @Operation(summary = "How close failures got", description = "Progress at expiry as a fraction of the "
            + "target. One-shot missions use the player's best score on the target map.")
    @GetMapping("/shortfall")
    public ResponseEntity<List<MissionShortfallResponse>> getShortfall(
            @RequestParam UUID templateId,
            @RequestParam(required = false) List<MissionBand> band,
            @RequestParam(required = false) List<String> tier,
            @RequestParam(required = false) List<String> categoryId,
            @RequestParam(required = false) Double skillMin,
            @RequestParam(required = false) Double skillMax,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(missionShortfallService.getShortfall(templateId,
                filter(null, null, null, categoryId, band, tier, skillMin, skillMax, country, from, to, 1)));
    }

    @Operation(summary = "Completion rate over time", description = "Whole percentage. unit is h, d, w or mo, amount "
            + "is how many back. Over 65 days rolls up to weekly.")
    @GetMapping("/charts/completion-rate")
    public ResponseEntity<List<TimeSeriesPointResponse>> getCompletionRate(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) List<MissionPool> pool,
            @RequestParam(required = false) List<MissionType> type,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) List<MissionBand> band,
            @RequestParam(required = false) List<String> tier,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(missionStatisticsService.getCompletionRateOverTime(amount, unit,
                filter(pool, type, templateId, null, band, tier, null, null, country, null, null, 1)));
    }

    @Operation(summary = "Missions done per day", description = "unit is h, d, w or mo, amount is how many back.")
    @GetMapping("/charts/completions-per-day")
    public ResponseEntity<List<TimeSeriesPointResponse>> getCompletionsPerDay(
            @RequestParam(defaultValue = "30") int amount,
            @RequestParam(defaultValue = "d") String unit,
            @RequestParam(required = false) List<MissionPool> pool,
            @RequestParam(required = false) List<MissionType> type,
            @RequestParam(required = false) List<String> tier,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(missionStatisticsService.getCompletionsPerDay(amount, unit,
                filter(pool, type, null, null, null, tier, null, null, country, null, null, 1)));
    }

    @Operation(summary = "Completions by type")
    @GetMapping("/charts/by-type")
    public ResponseEntity<List<DistributionEntryResponse>> getCompletionsByType(
            @RequestParam(required = false) List<MissionPool> pool,
            @RequestParam(required = false) List<String> tier,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(missionStatisticsService.getCompletionsByType(
                filter(pool, null, null, null, null, tier, null, null, country, from, to, 1)));
    }

    @Operation(summary = "Most missions done", description = "Filter by pool, type, tier or country.")
    @GetMapping("/leaderboards/most-completed")
    public ResponseEntity<Page<MissionCompletorResponse>> getMostCompleted(
            @RequestParam(required = false) List<MissionPool> pool,
            @RequestParam(required = false) List<MissionType> type,
            @RequestParam(required = false) List<String> tier,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(missionStatisticsService.getMostCompleted(
                filter(pool, type, null, null, null, tier, null, null, country, null, null, 1), pageable));
    }

    @Operation(summary = "Top mission XP earners", description = "Lifetime mission XP.")
    @GetMapping("/leaderboards/most-mission-xp")
    public ResponseEntity<Page<MissionCompletorResponse>> getMostMissionXp(
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(missionStatisticsService.getMostMissionXp(country, pageable));
    }

    private MissionStatsFilter filter(List<MissionPool> pools, List<MissionType> types, UUID templateId,
            List<String> categoryIds, List<MissionBand> bands, List<String> tiers, Double skillMin, Double skillMax,
            String country, Instant from, Instant to, int minAssigned) {
        List<UUID> resolvedCategories = categoryIds == null ? null
                : categoryIds.stream().map(categoryService::resolveId).filter(java.util.Objects::nonNull).toList();
        return new MissionStatsFilter(pools, types, templateId, resolvedCategories, bands, tiers,
                skillMin, skillMax, country, from, to, minAssigned);
    }
}
