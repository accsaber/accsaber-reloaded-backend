package com.accsaber.backend.controller.ranking;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.DifficultyRow;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.MapLeaderboard;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayerBoard;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.map.ComplexityComparisonService;
import com.accsaber.backend.service.map.ComplexityDatasetService;
import com.accsaber.backend.service.map.ComplexityScenario;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

    @Operation(summary = "Every difficulty under the three complexity scenarios", description = "One row per difficulty with what it carries today, what the old BeatLeader accuracy script says, and what the current note accuracy script says, side by side. Each scenario also brings the top AP, the average AP and the average weighted AP the map would pay if that complexity were live, worked out from every active score, plus the deltas against today. The estimates block holds the inputs each script used, so you can see why a number came out the way it did. Filter by category, and pass a status to look at the queue or the qualified maps, which only have complexities and no scores yet. Run the refresh complexity estimates job first if the estimate columns are empty.")
    @GetMapping("/difficulties")
    public ResponseEntity<List<DifficultyRow>> difficulties(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "RANKED") MapDifficultyStatus status) {
        return ResponseEntity.ok(comparisonService.difficulties(categoryId, status));
    }

    @Operation(summary = "One map's leaderboard under the three scenarios", description = "Every active score on the difficulty with the player attached, ordered by today's rank. Each row carries the AP, the weighted AP and the rank the play gets under each scenario and the deltas against today, so you can see who a reweight would move and by how much. The header is the same row the difficulties list gives you.")
    @GetMapping("/difficulties/{mapDifficultyId}/leaderboard")
    public ResponseEntity<MapLeaderboard> leaderboard(@PathVariable UUID mapDifficultyId) {
        return ResponseEntity.ok(comparisonService.leaderboard(mapDifficultyId));
    }

    @Operation(summary = "Maps with the highest average weighted AP under a scenario", description = "The same board the public statistics page has, priced under the scenario you pick, so you can see which maps would be the most worth farming if that script went live. Rows come back in the same shape as the difficulties list, with all three scenarios on each, sorted by the chosen one.")
    @GetMapping("/leaderboards/highest-avg-ap")
    public ResponseEntity<List<DifficultyRow>> highestAverageAp(
            @RequestParam(defaultValue = "CURRENT") ComplexityScenario scenario,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "10") int minScores,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(comparisonService.highestAverageAp(scenario, categoryId, minScores, limit));
    }

    @Operation(summary = "The player leaderboard under the three scenarios", description = "Players in today's order for a category, or Overall when you leave the category out, with their total AP and rank under each scenario and the deltas against today. The ladders block counts how many players hold a 900, a 1000 and an 1100 play under each scenario, which is the quickest read on whether a script inflates or deflates the top.")
    @GetMapping("/players")
    public ResponseEntity<PlayerBoard> players(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(comparisonService.players(categoryId, limit));
    }

    @Operation(summary = "Apply a scenario as a bulk reweight", description = "Turns the chosen estimate scenario into a real reweight of every ranked difficulty whose estimate differs from what it carries today, then reprices scores, boards, statistics, rankings and XP in the background. Ranking heads only. The reason lands on every complexity history row, so put the script version in it.")
    @PostMapping("/apply")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<Void> apply(
            @RequestParam ComplexityScenario scenario,
            @RequestParam String reason,
            Authentication authentication) {
        comparisonService.apply(scenario, reason, StaffPrincipals.linkedUserIdOf(authentication),
                StaffPrincipals.staffIdOf(authentication));
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Download every score on a ranked map as CSV", description = "Streams one row per score row on every ranked difficulty, history included, so you get the improvements and the attempts and not only the active play. Each row carries the map difficulty id, the category, the raw score and the map's max score so accuracy is yours to derive, the AP the score currently pays, the miss and cut counts, the modifiers as a pipe separated list, and the player's country and banned flags. Nothing is filtered for you here on purpose.")
    @GetMapping(value = "/dataset/scores", produces = CSV)
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public void scores(HttpServletResponse response) throws IOException {
        prepare(response, "accsaber-scores.csv");
        datasetService.writeScores(response.getOutputStream());
    }

    @Operation(summary = "Download every ranked, qualified and queued difficulty as CSV", description = "One row per difficulty with the song, mapper, hash, leaderboard ids, category, status, the complexity it carries right now, when it was ranked, the max score and the chart metadata like note count, duration and BPM. Pairs with the scores download through the map difficulty id.")
    @GetMapping(value = "/dataset/difficulties", produces = CSV)
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public void difficultiesCsv(HttpServletResponse response) throws IOException {
        prepare(response, "accsaber-difficulties.csv");
        datasetService.writeDifficulties(response.getOutputStream());
    }

    @Operation(summary = "Download the complexity history as CSV", description = "Every complexity value a ranked difficulty has ever carried, with the reason it was set and when, oldest first per difficulty.")
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
