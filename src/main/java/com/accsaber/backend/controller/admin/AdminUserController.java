package com.accsaber.backend.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.service.player.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Users")
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Ban a user", description = "Bans a user, excluding them from leaderboards and rankings. Profile remains accessible.")
    @PostMapping("/{userId}/ban")
    public ResponseEntity<Void> banUser(@PathVariable Long userId) {
        userService.setBanned(userId, true);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unban a user", description = "Unbans a previously banned user, restoring them to leaderboards and rankings.")
    @PostMapping("/{userId}/unban")
    public ResponseEntity<Void> unbanUser(@PathVariable Long userId) {
        userService.setBanned(userId, false);
        return ResponseEntity.noContent().build();
    }
}
