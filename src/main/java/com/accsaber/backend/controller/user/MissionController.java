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
@Tag(name = "Missions and Events")
public class MissionController {

    private final MissionQueryService missionQueryService;

    @Operation(summary = "Your missions", description = "Dailies reset at 4AM server time, weeklies on Monday. "
            + "completed=true gives finished ones, pool only filters the active list. Your clan skill missions "
            + "only show with pool=clan.")
    @GetMapping
    public ResponseEntity<List<MissionResponse>> listMine(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @RequestParam(defaultValue = "false") boolean completed,
            @RequestParam(required = false) MissionPool pool) {
        Long userId = requirePrincipal(principal).getUserId();
        List<UserMission> missions = resolveMissions(userId, completed, pool);
        return ResponseEntity.ok(missions.stream().map(MissionResponse::from).toList());
    }

    private List<UserMission> resolveMissions(Long userId, boolean completed, MissionPool pool) {
        if (completed) {
            return missionQueryService.listCompleted(userId);
        }
        return pool == null
                ? missionQueryService.listActive(userId)
                : missionQueryService.listActiveByPool(userId, pool);
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
