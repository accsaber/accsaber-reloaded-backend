package com.accsaber.backend.controller.ranking;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.DifficultyPage;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.MapLeaderboard;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayerBoard;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayerPlays;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.Preview;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.Rater;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.map.ComplexityComparisonService;
import com.accsaber.backend.service.map.ComplexityDatasetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/ranking/complexity")
@PreAuthorize("hasRole('RANKING')")
@RequiredArgsConstructor
@Tag(name = "Ranking - Complexity")
public class RankingComplexityController {

    private static final String CSV = "text/csv; charset=utf-8";

    private final ComplexityComparisonService comparisonService;
    private final ComplexityDatasetService datasetService;

    @Operation(summary = "Difficulties under both scenarios", description = "Summary counts the whole "
            + "filtered round. Run the refresh complexity estimates job first if estimate columns are empty. Pass "
            + "absolute to sort a delta column by size.")
    @GetMapping("/difficulties")
    public ResponseEntity<DifficultyPage> difficulties(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "RANKED") MapDifficultyStatus status,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean absolute) {
        return ResponseEntity.ok(comparisonService.difficulties(
                new ComplexityComparisonService.MapFilter(categoryId, status, batchId, search, pinned),
                new ComplexityComparisonService.Paging(page, size, sort, absolute)));
    }

    @Operation(summary = "One map's leaderboard, both scenarios", description = "Pass absolute to sort a "
            + "delta column by size. The header has the script's estimate inputs.")
    @GetMapping("/difficulties/{mapDifficultyId}/leaderboard")
    public ResponseEntity<MapLeaderboard> leaderboard(
            @PathVariable UUID mapDifficultyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean absolute) {
        return ResponseEntity.ok(comparisonService.leaderboard(mapDifficultyId,
                new ComplexityComparisonService.Paging(page, size, sort, absolute)));
    }

    @Operation(summary = "Preview one map's leaderboard", description = "Same as the map "
            + "leaderboard, with a PREVIEW scenario from the constants in the body.")
    @PostMapping("/preview/difficulties/{mapDifficultyId}/leaderboard")
    public ResponseEntity<MapLeaderboard> previewLeaderboard(
            @PathVariable UUID mapDifficultyId,
            @Valid @RequestBody ComplexityRaterSpec rater,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean absolute) {
        return ResponseEntity.ok(comparisonService.previewLeaderboard(rater, mapDifficultyId,
                new ComplexityComparisonService.Paging(page, size, sort, absolute)));
    }

    @Operation(summary = "Player leaderboard, both scenarios", description = "Leave category off for "
            + "Overall. Ladders count players with a 900, 1000 and 1100 play per scenario.")
    @GetMapping("/players")
    public ResponseEntity<PlayerBoard> players(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(comparisonService.players(
                new ComplexityComparisonService.PlayerQuery(categoryId, limit, search)));
    }

    @Operation(summary = "One player's best plays", description = "limit is "
            + "how many plays per category and scenario go into the list.")
    @GetMapping("/players/{userId}/plays")
    public ResponseEntity<PlayerPlays> playerPlays(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(comparisonService.playerPlays(userId, limit));
    }

    @Operation(summary = "Preview one player's plays", description = "Same as the "
            + "player plays view, with a PREVIEW scenario from the constants in the body.")
    @PostMapping("/preview/players/{userId}/plays")
    public ResponseEntity<PlayerPlays> previewPlayerPlays(
            @PathVariable Long userId,
            @Valid @RequestBody ComplexityRaterSpec rater,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(comparisonService.previewPlayerPlays(rater, userId, limit));
    }

    @Operation(summary = "Current rater constants", description = "Same shape the preview "
            + "endpoint takes as its body. Worst shares off the listed bands snap to the nearest one.")
    @GetMapping("/rater")
    public ResponseEntity<Rater> rater() {
        return ResponseEntity.ok(comparisonService.rater());
    }

    @Operation(summary = "Preview new constants", description = "Nothing "
            + "is stored. Changing the player play minimum or top play count refits the whole pool and takes a "
            + "moment. Send those on commit.")
    @PostMapping("/preview")
    public ResponseEntity<Preview> preview(
            @Valid @RequestBody ComplexityRaterSpec rater,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "RANKED") MapDifficultyStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean absolute,
            @RequestParam(defaultValue = "100") int playerLimit) {
        return ResponseEntity.ok(comparisonService.preview(rater,
                new ComplexityComparisonService.MapFilter(categoryId, status, null, search, pinned),
                new ComplexityComparisonService.Paging(page, size, sort, absolute), playerLimit));
    }

    @Operation(summary = "Apply the script", description = "Ranking heads only. Pinned maps are "
            + "skipped. Pass a batch to scope it, QUEUE or QUALIFIED to set those directly, a step limit to cap "
            + "each move.")
    @PostMapping("/apply")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<Void> apply(
            @RequestParam String reason,
            @RequestParam(required = false) Double maxStep,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(defaultValue = "RANKED") MapDifficultyStatus status,
            Authentication authentication) {
        comparisonService.apply(new ComplexityComparisonService.ApplyOptions(reason, maxStep, batchId, status),
                StaffPrincipals.linkedUserIdOf(authentication), StaffPrincipals.staffIdOf(authentication));
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Scores CSV", description = "Every score row on ranked "
            + "maps, history included. Nothing is filtered.")
    @GetMapping(value = "/dataset/scores", produces = CSV)
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public void scores(HttpServletResponse response) throws IOException {
        prepare(response, "accsaber-scores.csv");
        datasetService.writeScores(response.getOutputStream());
    }

    @Operation(summary = "Difficulties CSV", description = "Joins to "
            + "the scores CSV on map difficulty id.")
    @GetMapping(value = "/dataset/difficulties", produces = CSV)
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public void difficultiesCsv(HttpServletResponse response) throws IOException {
        prepare(response, "accsaber-difficulties.csv");
        datasetService.writeDifficulties(response.getOutputStream());
    }

    @Operation(summary = "Complexity history CSV", description = "Oldest first per difficulty.")
    @GetMapping(value = "/dataset/complexity-history", produces = CSV)
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public void complexityHistory(HttpServletResponse response) throws IOException {
        prepare(response, "accsaber-complexity-history.csv");
        datasetService.writeComplexityHistory(response.getOutputStream());
    }

    private static void prepare(HttpServletResponse response, String filename) {
        response.setContentType(CSV);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
    }
}
