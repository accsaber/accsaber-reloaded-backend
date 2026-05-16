package com.accsaber.backend.controller.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.user.ProfileUpdateRequest;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.player.ProfileCustomizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "Profile Customization")
public class ProfileCustomizationController {

    private final ProfileCustomizationService profileService;

    @Operation(summary = "Update the authenticated player's customizable profile fields", description = "Patch any subset of {name, bio, pinnedScores}. Changing the name automatically disables platform name sync (re-enable via sync.name setting). Bio is sanitized server-side. Pinned scores are replaced atomically.")
    @PatchMapping("/profile")
    public ResponseEntity<Void> updateProfile(
            @RequestBody ProfileUpdateRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long userId = requirePrincipal(principal).getUserId();
        if (request.getName() != null) {
            profileService.updateName(userId, request.getName());
        }
        if (request.getBio() != null) {
            profileService.updateBio(userId, request.getBio());
        }
        if (request.getPinnedScores() != null) {
            profileService.updatePinnedScores(userId, request.getPinnedScores());
        }
        return ResponseEntity.noContent().build();
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
