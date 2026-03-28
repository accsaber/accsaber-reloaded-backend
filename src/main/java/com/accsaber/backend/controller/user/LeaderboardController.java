package com.accsaber.backend.controller.user;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.player.LeaderboardResponse;
import com.accsaber.backend.model.dto.response.player.XpLeaderboardResponse;
import com.accsaber.backend.service.stats.LeaderboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/leaderboards")
@RequiredArgsConstructor
@Tag(name = "Leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @Operation(summary = "Global leaderboard", description = "Paginated global rankings for a category, sorted by ranking ascending. Optionally filter by player name search. Set inactiveUsers=false to hide ScoreSaber-inactive players")
    @GetMapping("/{categoryId}")
    public ResponseEntity<Page<LeaderboardResponse>> getGlobal(
            @PathVariable UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean inactiveUsers,
            @PageableDefault(size = 20, sort = "ranking", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(leaderboardService.getGlobal(categoryId, search, inactiveUsers, pageable));
    }

    @Operation(summary = "Country leaderboard", description = "Paginated rankings filtered by country for a category, sorted by AP descending. Optionally filter by player name search. Set inactiveUsers=false to hide ScoreSaber-inactive players")
    @GetMapping("/{categoryId}/country/{country}")
    public ResponseEntity<Page<LeaderboardResponse>> getByCountry(
            @PathVariable UUID categoryId,
            @PathVariable String country,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean inactiveUsers,
            @PageableDefault(size = 20, sort = "ranking", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(
                leaderboardService.getByCountry(categoryId, country, search, inactiveUsers, pageable));
    }

    @Operation(summary = "XP leaderboard", description = "Paginated XP rankings, sorted by total XP descending. Optional country and name search filters. Set inactiveUsers=false to hide ScoreSaber-inactive players")
    @GetMapping("/xp")
    public ResponseEntity<Page<XpLeaderboardResponse>> getXpLeaderboard(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "true") boolean inactiveUsers,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(leaderboardService.getXpLeaderboard(country, search, inactiveUsers, pageable));
    }
}
