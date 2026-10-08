package com.accsaber.backend.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.user.CountryOverrideRequest;
import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.scheduler.PlayerRefreshScheduler;
import com.accsaber.backend.service.player.PlayerImportService;
import com.accsaber.backend.service.player.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Players")
public class AdminUserController {

    private final UserService userService;
    private final PlayerImportService playerImportService;
    private final PlayerRefreshScheduler playerRefreshScheduler;

    @Operation(summary = "Ban or unban a player", description = "banned=true pulls a player off the leaderboards, "
            + "banned=false puts them back. Boards update shortly after the 202.")
    @PatchMapping("/{userId}/ban")
    public ResponseEntity<Void> setBanned(@PathVariable Long userId, @RequestParam boolean banned) {
        userService.setBanned(userId, banned);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Pin a player's country", description = "Send a country to pin it against "
            + "refreshes. Send null to let the next refresh change it. Pinning reruns rankings, unpinning does "
            + "not.")
    @PatchMapping("/{userId}/country")
    public ResponseEntity<Void> setCountryOverride(@PathVariable Long userId,
            @Valid @RequestBody CountryOverrideRequest request) {
        if (request.getCountry() == null) {
            userService.clearCountryOverride(userId);
        } else {
            userService.overrideCountry(userId, request.getCountry());
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Refresh one player")
    @PostMapping("/{userId}/refresh")
    public ResponseEntity<UserResponse> refreshPlayer(@PathVariable Long userId) {
        playerImportService.refreshPlayerProfile(userId);
        return ResponseEntity.ok(userService.findByUserId(userId));
    }

    @Operation(summary = "Refresh every player")
    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshAllPlayers() {
        playerRefreshScheduler.refreshAllPlayersAsync();
        return ResponseEntity.accepted().build();
    }
}
