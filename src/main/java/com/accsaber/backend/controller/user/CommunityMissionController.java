package com.accsaber.backend.controller.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.mission.MissionContributorResponse;
import com.accsaber.backend.model.dto.response.mission.MissionResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.mission.SharedMissionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/missions/community")
@RequiredArgsConstructor
@Tag(name = "Missions and Events")
public class CommunityMissionController {

    private final SharedMissionService sharedMissionService;

    @Operation(summary = "List community missions",
            description = "Missions the whole playerbase chips away at together, so the progress on one is everybody's "
                    + "put together rather than yours alone. Running ones by default; pass active=false to include the "
                    + "finished ones too. Pass eventId to narrow it to one event's, and note those only count plays from "
                    + "people who joined that event. If you are signed in you also get yourContribution on each, which is "
                    + "your own share of the bar in the same units the bar is measured in.")
    @GetMapping
    public ResponseEntity<List<MissionResponse>> list(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(defaultValue = "true") boolean active) {
        return ResponseEntity.ok(sharedMissionService.list(eventId, active, viewerId(principal)));
    }

    @Operation(summary = "Get one community mission",
            description = "A single community mission with where the whole community has got to on it, plus your own "
                    + "share when you are signed in.")
    @GetMapping("/{id}")
    public ResponseEntity<MissionResponse> get(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(sharedMissionService.get(id, viewerId(principal)));
    }

    @Operation(summary = "List who contributed to a community mission",
            description = "Everyone who put something into this one, biggest contribution first, with ties broken by who "
                    + "got there earliest. Once the mission is done, rewardedAt tells you whether that player has been "
                    + "paid out yet.")
    @GetMapping("/{id}/contributors")
    public ResponseEntity<Page<MissionContributorResponse>> contributors(@PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(sharedMissionService.leaderboard(id, pageable));
    }

    private Long viewerId(PlayerUserDetails principal) {
        return principal != null ? principal.getUserId() : null;
    }
}
