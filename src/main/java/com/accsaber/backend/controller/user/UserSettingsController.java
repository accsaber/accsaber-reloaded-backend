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
@Tag(name = "User Settings")
public class UserSettingsController {

    private final UserSettingsService settingsService;

    @Operation(summary = "Get all of the authenticated player's settings (with defaults)")
    @GetMapping("/me/settings")
    public ResponseEntity<Map<String, Object>> getMyAllSettings(
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(settingsService.getAll(requirePrincipal(principal).getUserId()));
    }

    @Operation(summary = "Get one settings group for the authenticated player (e.g. privacy, appearance)")
    @GetMapping("/me/settings/{group}")
    public ResponseEntity<Map<String, Object>> getMyGroup(
            @PathVariable String group,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(settingsService.getGroup(requirePrincipal(principal).getUserId(), group));
    }

    @Operation(summary = "Patch one settings group for the authenticated player. Body is a partial map of key→value.")
    @PutMapping("/me/settings/{group}")
    public ResponseEntity<Map<String, Object>> patchMyGroup(
            @PathVariable String group,
            @RequestBody Map<String, Object> patch,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(settingsService.updateGroup(requirePrincipal(principal).getUserId(), group, patch));
    }

    @Operation(summary = "Get publicly-readable settings for a player in one group", description = "Only keys whose registry entry is publicReadable=true are returned. Use to discover privacy preferences before fetching gated data.")
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
