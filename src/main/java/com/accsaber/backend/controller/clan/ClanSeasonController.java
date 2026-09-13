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

    @Operation(summary = "List clan seasons", description = "Every season, newest first. closedAt is set once a season has paid out.")
    @GetMapping("/seasons")
    public ResponseEntity<Page<ClanSeasonResponse>> seasons(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(seasonService.list(pageable));
    }

    @Operation(summary = "Get a clan season", description = "By slug or id, or pass current for the season running now.")
    @GetMapping("/seasons/{slugOrId}")
    public ResponseEntity<ClanSeasonResponse> season(@PathVariable String slugOrId) {
        return ResponseEntity.ok(seasonService.get(slugOrId));
    }

    @Operation(summary = "Rank clans by Standing for a season",
            description = "Standing is base plus earned. Base comes from the roster and from allies that have fought "
                    + "this season, and earned is what wars and missions added. A running season ranks live, and a "
                    + "closed one reads the final table it was frozen with.")
    @GetMapping("/seasons/{slugOrId}/standings")
    public ResponseEntity<Page<ClanStandingResponse>> standings(
            @PathVariable String slugOrId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(seasonService.standings(slugOrId, pageable));
    }

    @Operation(summary = "Get a clan's Standing",
            description = "The clan's rank and Standing for a season, the current one unless you pass season.")
    @GetMapping("/{clanId}/standing")
    public ResponseEntity<ClanStandingResponse> standing(
            @PathVariable UUID clanId,
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(standingService.standingOf(clanId, season));
    }

    @Operation(summary = "List a clan's Standing events",
            description = "Every change to the clan's earned Standing in a season, newest first.")
    @GetMapping("/{clanId}/standing/events")
    public ResponseEntity<Page<ClanStandingEventResponse>> standingEvents(
            @PathVariable UUID clanId,
            @RequestParam(required = false) String season,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(standingService.events(clanId, season, pageable));
    }
}
