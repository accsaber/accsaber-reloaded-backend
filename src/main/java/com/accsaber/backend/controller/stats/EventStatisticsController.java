package com.accsaber.backend.controller.stats;

import java.util.List;
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

import com.accsaber.backend.model.dto.response.statistics.EventMissionLeaderboardResponse;
import com.accsaber.backend.model.dto.response.statistics.EventParticipationResponse;
import com.accsaber.backend.model.dto.response.statistics.EventSummaryResponse;
import com.accsaber.backend.service.mission.EventService;
import com.accsaber.backend.service.stats.EventStatisticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/statistics/events")
@RequiredArgsConstructor
@Tag(name = "Site Statistics")
public class EventStatisticsController {

    private final EventStatisticsService eventStatisticsService;
    private final EventService eventService;

    @Operation(summary = "How one event went", description = "UUID or slug both work. Counts are per player. "
            + "completions is raw clears. week filters missions, numbered from 1. country narrows every number.")
    @GetMapping("/{idOrSlug}/summary")
    public ResponseEntity<EventSummaryResponse> getSummary(
            @PathVariable String idOrSlug,
            @RequestParam(required = false) Integer week,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(eventStatisticsService.getSummary(
                eventService.resolveId(idOrSlug), week, country));
    }

    @Operation(summary = "Top mission grinders", description = "Leave templateId off to count every mission in "
            + "the event. Ties share a rank. First one there wins.")
    @GetMapping("/{idOrSlug}/missions/leaderboard")
    public ResponseEntity<Page<EventMissionLeaderboardResponse>> getMissionLeaderboard(
            @PathVariable String idOrSlug,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(eventStatisticsService.getMissionLeaderboard(
                eventService.resolveId(idOrSlug), templateId, country, pageable));
    }

    @Operation(summary = "Compare events", description = "Newest first. Pass eventId more than once to "
            + "pick a few. country scopes the counts.")
    @GetMapping("/participation")
    public ResponseEntity<Page<EventParticipationResponse>> getParticipation(
            @RequestParam(required = false) List<String> eventId,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        List<UUID> resolved = eventId == null ? null : eventId.stream().map(eventService::resolveId).toList();
        return ResponseEntity.ok(eventStatisticsService.getParticipation(resolved, country, pageable));
    }
}
