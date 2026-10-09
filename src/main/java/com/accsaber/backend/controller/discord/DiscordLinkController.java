package com.accsaber.backend.controller.discord;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.discord.UpdateDiscordLinkRequest;
import com.accsaber.backend.model.dto.response.DiscordLinkResponse;
import com.accsaber.backend.service.discord.DiscordLinkService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/discord/links")
@RequiredArgsConstructor
@Tag(name = "Admin - Players")
public class DiscordLinkController {

    private final DiscordLinkService discordLinkService;

    @Operation(summary = "Link by Discord ID")
    @GetMapping("/{discordId}")
    public ResponseEntity<DiscordLinkResponse> getByDiscordId(@PathVariable String discordId) {
        return ResponseEntity.ok(discordLinkService.findByDiscordId(discordId));
    }

    @Operation(summary = "Link by user ID")
    @GetMapping("/user/{userId}")
    public ResponseEntity<DiscordLinkResponse> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(discordLinkService.findByUserId(userId));
    }

    @Operation(summary = "Repoint a Discord link")
    @PatchMapping("/{discordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DiscordLinkResponse> update(@PathVariable String discordId,
            @Valid @RequestBody UpdateDiscordLinkRequest request) {
        return ResponseEntity.ok(discordLinkService.update(discordId, request));
    }

    @Operation(summary = "Unlink a Discord account")
    @DeleteMapping("/{discordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unlink(@PathVariable String discordId) {
        discordLinkService.unlink(discordId);
        return ResponseEntity.noContent().build();
    }
}
