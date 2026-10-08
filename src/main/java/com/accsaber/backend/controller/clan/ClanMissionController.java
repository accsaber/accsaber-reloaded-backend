package com.accsaber.backend.controller.clan;

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
import com.accsaber.backend.service.clan.ClanMissionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/clans")
@RequiredArgsConstructor
@Tag(name = "Clans")
public class ClanMissionController {

    private final ClanMissionService missionService;

    @Operation(summary = "Clan missions",
            description = "This week by default. current=false for finished ones. Skill mission targets are per "
                    + "member, find yours under your missions with pool=clan.")
    @GetMapping("/{clanId}/missions")
    public ResponseEntity<Page<MissionResponse>> missions(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @RequestParam(defaultValue = "true") boolean current,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(missionService.list(clanId, current,
                principal != null ? principal.getUserId() : null, pageable));
    }

    @Operation(summary = "Mission contributors",
            description = "Biggest first, ties to whoever got there first. Reward items go out in this order.")
    @GetMapping("/{clanId}/missions/{missionId}/contributors")
    public ResponseEntity<Page<MissionContributorResponse>> contributors(
            @PathVariable UUID clanId,
            @PathVariable UUID missionId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(missionService.contributors(clanId, missionId, pageable));
    }
}
