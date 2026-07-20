package com.accsaber.backend.controller.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.map.LeaderboardPlatform;
import com.accsaber.backend.service.score.ScoreImportService;
import com.accsaber.backend.service.score.ScoreIngestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class AdminImportController {

    private final ScoreImportService scoreImportService;
    private final ScoreIngestionService scoreIngestionService;

    @Operation(summary = "Backfill all ranked difficulties")
    @PostMapping("/scores/backfill-all")
    public ResponseEntity<Void> backfillAll() {
        scoreImportService.backfillAllRankedDifficulties();
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Backfill a specific difficulty")
    @PostMapping("/scores/backfill/{difficultyId}")
    public ResponseEntity<Void> backfillDifficulty(@PathVariable UUID difficultyId) {
        scoreImportService.backfillDifficultyAsync(difficultyId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Backfill multiple difficulties")
    @PostMapping("/scores/backfill-difficulties")
    public ResponseEntity<Void> backfillDifficulties(@RequestBody List<UUID> difficultyIds) {
        scoreImportService.backfillDifficultiesAsync(difficultyIds);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Backfill a user across all ranked difficulties")
    @PostMapping("/scores/backfill-user/{userId}")
    public ResponseEntity<Void> backfillUser(@PathVariable Long userId) {
        scoreImportService.backfillUserAsync(userId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Backfill multiple users across all ranked difficulties")
    @PostMapping("/scores/backfill-users")
    public ResponseEntity<Void> backfillUsers(@RequestBody List<Long> userIds) {
        scoreImportService.backfillUsersAsync(userIds);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Enrich active scores from a point in time", description = "Walks each ranked difficulty's recent scores back until the given instant, merging missing platform data onto the matching active score row. "
            + "Never creates or supersedes scores: a fetched score with no matching active row is skipped. "
            + "Omit platform to sweep both; pass SCORESABER to fetch only the ScoreSaber half.")
    @PostMapping("/scores/gap-fill")
    public ResponseEntity<Void> gapFillSince(
            @RequestParam Instant since,
            @RequestParam(required = false) LeaderboardPlatform platform) {
        if (since.isAfter(Instant.now())) {
            throw new ValidationException("since must be in the past");
        }
        scoreIngestionService.gapFillSince(since, platform);
        return ResponseEntity.accepted().build();
    }

}
