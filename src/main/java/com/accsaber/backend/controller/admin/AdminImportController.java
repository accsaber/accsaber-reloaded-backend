package com.accsaber.backend.controller.admin;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.map.ImportMapFromLeaderboardIdsRequest;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.scheduler.PlayerRefreshScheduler;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.map.MapImportService;
import com.accsaber.backend.service.player.PlayerImportService;
import com.accsaber.backend.service.player.UserService;
import com.accsaber.backend.service.score.ScoreImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/import")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class AdminImportController {

    private final MapImportService mapImportService;
    private final ScoreImportService scoreImportService;
    private final PlayerImportService playerImportService;
    private final PlayerRefreshScheduler playerRefreshScheduler;
    private final UserService userService;

    @Operation(summary = "Import map by leaderboard IDs (Ranked)")
    @PostMapping("/maps")
    public ResponseEntity<MapDifficultyResponse> importMap(
            @Valid @RequestBody ImportMapFromLeaderboardIdsRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        MapDifficultyResponse response = mapImportService.importByLeaderboardIds(
                request, userDetails.getStaffUser().getId(), MapDifficultyStatus.RANKED);
        scoreImportService.backfillDifficultyAsync(response.getId());
        return ResponseEntity.created(URI.create("/v1/maps/difficulties/" + response.getId()))
                .body(response);
    }

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

    @Operation(summary = "Refresh a player's profile")
    @PostMapping("/players/{steamId}/refresh")
    public ResponseEntity<UserResponse> refreshPlayer(@PathVariable Long steamId) {
        playerImportService.refreshPlayerProfile(steamId);
        return ResponseEntity.ok(userService.findBySteamId(steamId));
    }

    @Operation(summary = "Refresh all player profiles")
    @PostMapping("/players/refresh-all")
    public ResponseEntity<Void> refreshAllPlayers() {
        playerRefreshScheduler.refreshAllPlayersAsync();
        return ResponseEntity.accepted().build();
    }
}
