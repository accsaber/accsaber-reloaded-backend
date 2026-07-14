package com.accsaber.backend.controller.user;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.response.mission.MissionResponse;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.mission.MissionQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users/me/missions")
@RequiredArgsConstructor
@Tag(name = "Missions")
public class MissionController {

    private final MissionQueryService missionQueryService;

    @Operation(summary = "List the authenticated player's active missions")
    @GetMapping
    public ResponseEntity<List<MissionResponse>> listMine(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @RequestParam(required = false) MissionPool pool) {
        Long userId = requirePrincipal(principal).getUserId();
        List<UserMission> missions = pool == null
                ? missionQueryService.listActive(userId)
                : missionQueryService.listActiveByPool(userId, pool);
        return ResponseEntity.ok(missions.stream().map(MissionResponse::from).toList());
    }

    @Operation(summary = "List the authenticated player's completed missions")
    @GetMapping("/completed")
    public ResponseEntity<List<MissionResponse>> listCompleted(
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long userId = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(
                missionQueryService.listCompleted(userId).stream()
                        .map(MissionResponse::from).toList());
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
