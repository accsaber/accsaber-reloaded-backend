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

    @Operation(summary = "List your missions", description = "The missions currently open for whoever the token belongs to, "
            + "with the target on each and how far along they are. Dailies roll over at 4AM server time and weeklies go on "
            + "Monday, so anything not finished by then disappears. Pass completed=true to get the ones you have already "
            + "finished instead, which are kept around after they roll over so you can look back at what you earned. Pool "
            + "narrows the active list to one kind, and is ignored on the completed one. Your part of your clan's skill "
            + "missions only shows up when you ask for pool=clan.")
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
