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

    @Operation(summary = "Community missions",
            description = "Running ones by default. Pass active=false for finished ones too. Event missions only "
                    + "count plays from event joiners. Signed in, you get yourContribution.")
    @GetMapping
    public ResponseEntity<List<MissionResponse>> list(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(defaultValue = "true") boolean active) {
        return ResponseEntity.ok(sharedMissionService.list(eventId, active, viewerId(principal)));
    }

    @Operation(summary = "Get a community mission",
            description = "Has your own share when you are signed in.")
    @GetMapping("/{id}")
    public ResponseEntity<MissionResponse> get(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(sharedMissionService.get(id, viewerId(principal)));
    }

    @Operation(summary = "Mission contributors",
            description = "Biggest first, ties by who got there first. rewardedAt shows if they got paid.")
    @GetMapping("/{id}/contributors")
    public ResponseEntity<Page<MissionContributorResponse>> contributors(@PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(sharedMissionService.leaderboard(id, pageable));
    }

    private Long viewerId(PlayerUserDetails principal) {
        return principal != null ? principal.getUserId() : null;
    }
}
