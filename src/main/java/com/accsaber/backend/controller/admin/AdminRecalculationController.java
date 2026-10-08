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
import com.accsaber.backend.service.skill.SkillService;
import com.accsaber.backend.service.stats.StatisticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/recalculate")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Operations")
public class AdminRecalculationController {

    private final ScoreCorrectionService scoreCorrectionService;
    private final StatisticsService statisticsService;
    private final SkillService skillService;

    @Operation(summary = "Recalc a player's category stats")
    @PostMapping("/stats/player/{userId}")
    public ResponseEntity<Void> recalculatePlayer(@PathVariable Long userId,
            @RequestParam UUID categoryId) {
        statisticsService.recalculate(userId, categoryId);
        skillService.upsertSkill(userId, categoryId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Remove a misattributed score",
            description = "Deactivates the score, takes back the XP and recalculates rankings and stats.")
    @PostMapping("/scores/remove")
    public ResponseEntity<Void> removeScore(@RequestParam Long userId,
            @RequestParam UUID mapDifficultyId,
            @RequestParam(required = false) String reason) {
        scoreCorrectionService.removeScore(userId, mapDifficultyId, reason);
        return ResponseEntity.ok().build();
    }
}
