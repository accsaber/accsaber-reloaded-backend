package com.accsaber.backend.controller.user;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.response.mission.EventDetailResponse;
import com.accsaber.backend.model.dto.response.mission.EventMissionResponse;
import com.accsaber.backend.model.dto.response.mission.EventProgressResponse;
import com.accsaber.backend.model.dto.response.mission.EventResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.mission.EventMissionService;
import com.accsaber.backend.service.mission.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events")
public class EventController {

    private final EventService eventService;
    private final EventMissionService eventMissionService;

    @Operation(summary = "List events", description = "Optional state filter: live, upcoming or past.")
    @GetMapping
    public ResponseEntity<List<EventResponse>> list(@RequestParam(required = false) String state) {
        return ResponseEntity.ok(eventService.listPublic(state).stream()
                .map(EventResponse::from).toList());
    }

    @Operation(summary = "Get the currently running event",
            description = "204 when no event is live.")
    @GetMapping("/current")
    public ResponseEntity<EventResponse> current() {
        return eventService.findCurrent()
                .map(event -> ResponseEntity.ok(EventResponse.from(event)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "List an event's missions",
            description = "Optional week filter, 1-based from the event start (week 1 = first 7 days).")
    @GetMapping("/{id}/missions")
    public ResponseEntity<List<EventMissionResponse>> missions(@PathVariable UUID id,
            @RequestParam(required = false) Integer week) {
        return ResponseEntity.ok(eventMissionService.getMissions(id, week));
    }

    @Operation(summary = "List an event's missions with the authenticated player's progress",
            description = "Optional week filter. Lazily assigns any unlocked event missions the player is missing.")
    @GetMapping("/{id}/missions/me")
    public ResponseEntity<List<EventProgressResponse.EventMissionProgressResponse>> myMissions(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID id,
            @RequestParam(required = false) Integer week) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        Long userId = principal.getUserId();
        eventMissionService.ensureForUserAndEventId(userId, id);
        return ResponseEntity.ok(eventMissionService.getMissionsWithProgress(userId, id, week));
    }

    @Operation(summary = "Begin an event for the authenticated player",
            description = "Opt-in: creates the player's event profile and rolls out the first unlocked week of "
                    + "missions. Later weeks stay locked until the current week is completed. Idempotent.")
    @PostMapping("/{id}/begin")
    public ResponseEntity<EventProgressResponse> begin(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID id) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return ResponseEntity.ok(eventMissionService.begin(principal.getUserId(), id));
    }

    @Operation(summary = "Get an event with its missions")
    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(eventMissionService.getDetail(id));
    }

    @Operation(summary = "Get an event with the authenticated player's progress",
            description = "Lazily assigns any unlocked event missions the player is missing.")
    @GetMapping("/{id}/me")
    public ResponseEntity<EventProgressResponse> getMyProgress(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID id) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        Long userId = principal.getUserId();
        eventMissionService.ensureForUserAndEventId(userId, id);
        return ResponseEntity.ok(eventMissionService.getProgress(userId, id));
    }
}
