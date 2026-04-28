package com.accsaber.backend.controller.admin;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.service.score.ScoreCorrectionService;
import com.accsaber.backend.service.score.ScoreRecalculationService;
import com.accsaber.backend.service.score.XPReweightService;
import com.accsaber.backend.service.skill.SkillService;
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

    private final ScoreCorrectionService scoreCorrectionService;
    private final ScoreRecalculationService scoreRecalculationService;
    private final StatisticsService statisticsService;
    private final XPReweightService xpReweightService;
    private final SkillService skillService;

    @Operation(summary = "Recalculate raw AP for a single difficulty",
            description = "Versioned recalc: creates new score versions, reassigns ranks, updates stats.")
    @PostMapping("/ap/difficulty/{difficultyId}")
    public ResponseEntity<Void> recalculateDifficulty(@PathVariable UUID difficultyId) {
        scoreRecalculationService.recalculateDifficultyAsync(difficultyId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate raw AP for all scores",
            description = "In-place AP recalc across all ranked difficulties. Reassigns ranks and updates stats per category.")
    @PostMapping("/ap/raw")
    public ResponseEntity<Void> recalculateAllRawAp() {
        scoreRecalculationService.recalculateAllRawApAsync();
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate weighted AP for all users",
            description = "Recalculates weighted AP per category for every user, then updates all rankings.")
    @PostMapping("/ap/weighted")
    public ResponseEntity<Void> recalculateAllWeightedAp() {
        scoreRecalculationService.recalculateAllWeightedApAsync();
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate all AP (raw + weighted)",
            description = "Runs raw AP recalc then weighted AP recalc sequentially.")
    @PostMapping("/ap/all")
    public ResponseEntity<Void> recalculateAllAp() {
        scoreRecalculationService.recalculateAllApAsync();
        return ResponseEntity.accepted().build();
    }

@Operation(summary = "Reweight XP for all scores",
            description = "Recalculates per-score XP based on the current XP curve. Does not update user totals.")
    @PostMapping("/xp/scores")
    public ResponseEntity<Void> reweightAllXp() {
        xpReweightService.reweightAllScores();
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate total XP for all users",
            description = "Recomputes totalXp from score XP + milestone XP + set bonus XP. Does not recalculate per-score XP.")
    @PostMapping("/xp/sum")
    public ResponseEntity<Void> recalculateTotalXp() {
        xpReweightService.recalculateTotalXpForAllUsers();
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Recalculate a player's statistics for a category")
    @PostMapping("/stats/player/{userId}")
    public ResponseEntity<Void> recalculatePlayer(@PathVariable Long userId,
            @RequestParam UUID categoryId) {
        statisticsService.recalculate(userId, categoryId);
        skillService.upsertSkill(userId, categoryId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Remove a wrongly-attributed score",
            description = "Deactivates a user's active score on a map difficulty, reverses XP, and recalculates rankings/stats.")
    @PostMapping("/scores/remove")
    public ResponseEntity<Void> removeScore(@RequestParam Long userId,
            @RequestParam UUID mapDifficultyId,
            @RequestParam(required = false) String reason) {
        scoreCorrectionService.removeScore(userId, mapDifficultyId, reason);
        return ResponseEntity.ok().build();
    }
}
