package com.accsaber.backend.controller.admin;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

}
