package com.accsaber.backend.controller.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.service.score.ScoreImportService;

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

    @Operation(summary = "Backfill a user across all ranked BL difficulties")
    @PostMapping("/scores/backfill-user/{userId}")
    public ResponseEntity<Void> backfillUser(@PathVariable Long userId) {
        scoreImportService.backfillUserAsync(userId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Backfill multiple users across all ranked BL difficulties")
    @PostMapping("/scores/backfill-users")
    public ResponseEntity<Void> backfillUsers(@RequestBody List<Long> userIds) {
        scoreImportService.backfillUsersAsync(userIds);
        return ResponseEntity.accepted().build();
    }

}
