package com.accsaber.backend.controller.user;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
@Tag(name = "Players")
public class ProfileCustomizationController {

    private final ProfileCustomizationService profileService;

    @Operation(summary = "Edit your profile", description = "Send only what changes. Setting a name turns off name "
            + "sync. sync.name turns it back on. Pinned scores get replaced as a set.")
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
        if (request.getPinnedMilestones() != null) {
            profileService.updatePinnedMilestones(userId, request.getPinnedMilestones());
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload your avatar", description = "Turns off avatar sync. sync.avatar turns it back on. "
            + "Use the returned URL.")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadAvatar(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long userId = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(profileService.updateAvatar(userId, file));
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
