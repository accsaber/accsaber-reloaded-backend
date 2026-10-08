package com.accsaber.backend.controller.user;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.player.UserSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Players")
public class UserSettingsController {

    private final UserSettingsService settingsService;

    @Operation(summary = "All your settings", description = "Unset keys come back as defaults.")
    @GetMapping("/me/settings")
    public ResponseEntity<Map<String, Object>> getMyAllSettings(
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(settingsService.getAll(requirePrincipal(principal).getUserId()));
    }

    @Operation(summary = "One settings group", description = "Defaults filled in.")
    @GetMapping("/me/settings/{group}")
    public ResponseEntity<Map<String, Object>> getMyGroup(
            @PathVariable String group,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(settingsService.getGroup(requirePrincipal(principal).getUserId(), group));
    }

    @Operation(summary = "Change a settings group", description = "Send only the keys you want to change. "
            + "Returns the whole group.")
    @PutMapping("/me/settings/{group}")
    public ResponseEntity<Map<String, Object>> patchMyGroup(
            @PathVariable String group,
            @RequestBody Map<String, Object> patch,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(settingsService.updateGroup(requirePrincipal(principal).getUserId(), group, patch));
    }

    @Operation(summary = "Someone's public settings", description = "Only the keys they made public.")
    @GetMapping("/{userId}/settings/{group}")
    public ResponseEntity<Map<String, Object>> getPublicGroup(
            @PathVariable Long userId,
            @PathVariable String group) {
        return ResponseEntity.ok(settingsService.getPublicGroup(userId, group));
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
