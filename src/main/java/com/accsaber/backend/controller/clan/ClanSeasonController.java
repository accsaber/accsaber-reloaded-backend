package com.accsaber.backend.controller.clan;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.clan.ClanSeasonResponse;
import com.accsaber.backend.model.dto.response.clan.ClanStandingEventResponse;
import com.accsaber.backend.model.dto.response.clan.ClanStandingResponse;
import com.accsaber.backend.service.clan.ClanSeasonService;
import com.accsaber.backend.service.clan.ClanStandingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/clans")
@RequiredArgsConstructor
@Tag(name = "Clans")
public class ClanSeasonController {

    private final ClanSeasonService seasonService;
    private final ClanStandingService standingService;

    @Operation(summary = "List clan seasons", description = "Newest first. closedAt is set once a season has paid "
            + "out.")
    @GetMapping("/seasons")
    public ResponseEntity<Page<ClanSeasonResponse>> seasons(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(seasonService.list(pageable));
    }

    @Operation(summary = "Get a clan season", description = "By slug or id. Pass current for the season running now.")
    @GetMapping("/seasons/{slugOrId}")
    public ResponseEntity<ClanSeasonResponse> season(@PathVariable String slugOrId) {
        return ResponseEntity.ok(seasonService.get(slugOrId));
    }

    @Operation(summary = "Season Standing leaderboard",
            description = "Standing is base plus earned. Running seasons rank live. Closed ones show the final table.")
    @GetMapping("/seasons/{slugOrId}/standings")
    public ResponseEntity<Page<ClanStandingResponse>> standings(
            @PathVariable String slugOrId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(seasonService.standings(slugOrId, pageable));
    }

    @Operation(summary = "A clan's Standing",
            description = "Current season unless you pass season.")
    @GetMapping("/{clanId}/standing")
    public ResponseEntity<ClanStandingResponse> standing(
            @PathVariable UUID clanId,
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(standingService.standingOf(clanId, season));
    }

    @Operation(summary = "Clan Standing history",
            description = "Every earned Standing change in a season, newest first.")
    @GetMapping("/{clanId}/standing/events")
    public ResponseEntity<Page<ClanStandingEventResponse>> standingEvents(
            @PathVariable UUID clanId,
            @RequestParam(required = false) String season,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(standingService.events(clanId, season, pageable));
    }
}
