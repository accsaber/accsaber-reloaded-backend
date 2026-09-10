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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.map.ComplexityRaterSpec;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.DifficultyRow;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.MapLeaderboard;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayerBoard;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.PlayerPlays;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.Preview;
import com.accsaber.backend.model.dto.response.admin.ComplexityComparisonResponse.Rater;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.map.ComplexityComparisonService;
import com.accsaber.backend.service.map.ComplexityDatasetService;
import com.accsaber.backend.service.map.ComplexityScenario;

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

    @Operation(summary = "Every difficulty under the three stored complexity scenarios", description = "One row per difficulty with what it carries today, what the old BeatLeader accuracy script says, and what the current note accuracy script says, side by side. Each scenario also brings the top AP, the average AP and the average weighted AP the map would pay if that complexity were live, worked out from every active score, plus the deltas against today. The estimates block holds the inputs each script used. That is where you look when a number surprises you. Filter by category, pass a status to look at the queue or the qualified maps, which only have complexities and no scores yet, and pass a search to match on song name, subtitle, artist or mapper, accents and case ignored. Run the refresh complexity estimates job first if the estimate columns are empty.")
    @GetMapping("/difficulties")
    public ResponseEntity<List<DifficultyRow>> difficulties(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "RANKED") MapDifficultyStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(comparisonService.difficulties(
                new ComplexityComparisonService.MapFilter(categoryId, status, search)));
    }

    @Operation(summary = "One map's leaderboard under the three scenarios", description = "Every active score on the difficulty with the player attached, ordered by today's rank. Each row carries the AP, the weighted AP and the rank the play gets under each scenario and the deltas against today. It answers who a reweight would move and by how much. The header is the same row the difficulties list gives you.")
    @GetMapping("/difficulties/{mapDifficultyId}/leaderboard")
    public ResponseEntity<MapLeaderboard> leaderboard(@PathVariable UUID mapDifficultyId) {
        return ResponseEntity.ok(comparisonService.leaderboard(mapDifficultyId));
    }

    @Operation(summary = "Maps with the highest average weighted AP under a scenario", description = "The same board the public statistics page has, priced under the scenario you pick. It shows which maps would be the most worth farming if that script went live. Rows come back in the same shape as the difficulties list, with all three scenarios on each, sorted by the chosen one. A search narrows the rows after ranking, so the positions still count the whole board. Each scenario block also carries the map's position on that scenario's board, ranked across every map that clears the score minimum and the category filter rather than the returned slice alone, with the position change against today in the deltas. A map with no complexity under a scenario, or under the score minimum, has no position there.")
    @GetMapping("/leaderboards/highest-avg-ap")
    public ResponseEntity<List<DifficultyRow>> highestAverageAp(
            @RequestParam(defaultValue = "CURRENT") ComplexityScenario scenario,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "10") int minScores,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(comparisonService.highestAverageAp(scenario,
                new ComplexityComparisonService.MapFilter(categoryId, MapDifficultyStatus.RANKED, search), minScores,
                limit));
    }

    @Operation(summary = "The player leaderboard under the three scenarios", description = "Players in today's order for a category, or Overall when you leave the category out, with their total AP and rank under each scenario and the deltas against today. The ladders block counts how many players hold a 900, a 1000 and an 1100 play under each scenario, which is the quickest read on whether a script inflates or deflates the top. A search matches any name a player has held, and their rank stays their real one.")
    @GetMapping("/players")
    public ResponseEntity<PlayerBoard> players(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(comparisonService.players(
                new ComplexityComparisonService.PlayerQuery(categoryId, limit, search)));
    }

    @Operation(summary = "One player's best plays per category under the three scenarios", description = "For every active category the player has ranked plays in: their total AP and rank under each scenario with deltas, and the union of their best plays under each scenario, ordered by today's AP. Each play carries the map row the difficulties list uses, the accuracy, and per scenario the AP, the weighted AP, the play's position in the player's list, which is what sets its weight, and its rank on the map, with deltas against today. Pass a limit for how many plays per category and scenario feed the union.")
    @GetMapping("/players/{userId}/plays")
    public ResponseEntity<PlayerPlays> playerPlays(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(comparisonService.playerPlays(userId, limit));
    }

    @Operation(summary = "One player's best plays per category under a set of constants", description = "The same view as the player plays endpoint, with CURRENT and a PREVIEW scenario priced from the constants in the body. The preview state is kept for a short while per set of constants, so opening several players after one tuning pass does not reprice the pool each time.")
    @PostMapping("/preview/players/{userId}/plays")
    public ResponseEntity<PlayerPlays> previewPlayerPlays(
            @PathVariable Long userId,
            @Valid @RequestBody ComplexityRaterSpec rater,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(comparisonService.previewPlayerPlays(rater, userId, limit));
    }

    @Operation(summary = "The constants the note accuracy script runs with", description = "The worst share and the per category intercept and slopes the backend is configured with right now, in the same shape the preview endpoint takes as its body. A panel loads them, lets staff nudge them and sends them back. The chart line prices a map from its notes alone and is what import uses. The board line adds the map's leaderboard ease, read from the top players by fitted skill in that category, and blends in between the board gate's minimum and full score counts once enough of those players have played the map. Its move off the chart line is capped at the gate's max nudge. The worst bands list says which worst shares the stored estimates carry exactly. Any other worst share snaps to the nearest band in a preview.")
    @GetMapping("/rater")
    public ResponseEntity<Rater> rater() {
        return ResponseEntity.ok(comparisonService.rater());
    }

    @Operation(summary = "Price every map with different constants without storing anything", description = "Takes a full set of rater constants and reprices every map that has a note accuracy estimate from the inputs that estimate already carries, then reprices every active score under those complexities. Nothing touches the model, BeatSaver or the database. It is cheap enough to call on every slider change. The answer holds the difficulties list with a CURRENT and a PREVIEW scenario per row, and the player board for the chosen category with both ladders. You see whether the constants pin 1100 to the elite and 1000 to the top fifty before asking for a backend change.")
    @PostMapping("/preview")
    public ResponseEntity<Preview> preview(
            @Valid @RequestBody ComplexityRaterSpec rater,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "RANKED") MapDifficultyStatus status,
            @RequestParam(defaultValue = "100") int playerLimit) {
        return ResponseEntity.ok(comparisonService.preview(rater,
                new ComplexityComparisonService.MapFilter(categoryId, status, null), playerLimit));
    }

    @Operation(summary = "Apply a scenario as a bulk reweight", description = "Turns the chosen estimate scenario into a real reweight of every ranked difficulty whose estimate differs from what it carries today, then reprices scores, boards, statistics, rankings and XP in the background. Ranking heads only. The reason lands on every complexity history row. Put the script version in it. Pass a step limit to move no map by more than that amount this round, so a map whose leaderboard keeps grinding settles over several rounds instead of dropping at once. Leave it out for a full correction.")
    @PostMapping("/apply")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<Void> apply(
            @RequestParam ComplexityScenario scenario,
            @RequestParam String reason,
            @RequestParam(required = false) Double maxStep,
            Authentication authentication) {
        comparisonService.apply(scenario, new ComplexityComparisonService.ApplyOptions(reason, maxStep),
                StaffPrincipals.linkedUserIdOf(authentication), StaffPrincipals.staffIdOf(authentication));
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Download every score on a ranked map as CSV", description = "Streams one row per score row on every ranked difficulty, history included. You get the improvements and the attempts as well as the active play. Each row carries the map difficulty id, the category, the raw score and the map's max score for deriving accuracy, the AP the score currently pays, the miss and cut counts, the modifiers as a pipe separated list, and the player's country and banned flags. Nothing is filtered for you here on purpose.")
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
