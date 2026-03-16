package com.accsaber.backend.controller.admin;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.service.score.ScoreRecalculationService;
import com.accsaber.backend.service.score.XPReweightService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/recalculate")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Recalculation")
public class AdminRecalculationController {

    private final ScoreRecalculationService scoreRecalculationService;
    private final StatisticsService statisticsService;
    private final RankingService rankingService;
    private final XPReweightService xpReweightService;

    @Operation(summary = "Recalculate all scores in a category")
    @PostMapping("/leaderboard/{categoryId}")
    public ResponseEntity<Void> recalculateLeaderboard(@PathVariable UUID categoryId) {
        rankingService.updateRankings(categoryId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate scores for a difficulty")
    @PostMapping("/difficulty/{difficultyId}")
    public ResponseEntity<Void> recalculateDifficulty(@PathVariable UUID difficultyId) {
        scoreRecalculationService.recalculateScoresAsync(difficultyId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate a player's statistics")
    @PostMapping("/player/{steamId}")
    public ResponseEntity<Void> recalculatePlayer(@PathVariable Long steamId,
            @RequestParam UUID categoryId) {
        statisticsService.recalculate(steamId, categoryId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate all scores in a category after AP curve change")
    @PostMapping("/category/{categoryId}/ap-curve")
    public ResponseEntity<Void> recalculateCategoryApCurve(@PathVariable UUID categoryId) {
        scoreRecalculationService.recalculateAllScoresForCategoryAsync(categoryId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate weighted AP for all users after weight curve change")
    @PostMapping("/weight-curve/{curveId}")
    public ResponseEntity<Void> recalculateWeightCurve(@PathVariable UUID curveId) {
        scoreRecalculationService.recalculateWeightCurveAsync(curveId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Reweight XP for all scores")
    @PostMapping("/xp-reweight")
    public ResponseEntity<Void> reweightAllXp() {
        xpReweightService.reweightAllScores();
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate total XP for all users",
            description = "Recomputes totalXp from score XP + milestone XP + set bonus XP. Does not recalculate per-score XP.")
    @PostMapping("/total-xp")
    public ResponseEntity<Void> recalculateTotalXp() {
        xpReweightService.recalculateTotalXpForAllUsers();
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate weighted AP for all scores in all categories",
            description = "Recalculates weighted AP per category for every user, then updates all rankings. Runs async.")
    @PostMapping("/weighted-ap")
    public ResponseEntity<Void> recalculateAllWeightedAp() {
        scoreRecalculationService.recalculateAllWeightedApAsync();
        return ResponseEntity.accepted().build();
    }
}
