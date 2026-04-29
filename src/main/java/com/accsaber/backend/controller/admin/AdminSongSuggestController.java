package com.accsaber.backend.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.service.songsuggest.SongSuggestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/songsuggest")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Song Suggest")
public class AdminSongSuggestController {

    private final SongSuggestService songSuggestService;

    @Operation(summary = "Regenerate the Song Suggest leaderboard now",
            description = "Runs the same pipeline as the weekly schedule. Async — returns 202 immediately.")
    @PostMapping("/regenerate")
    public ResponseEntity<Void> regenerate() {
        songSuggestService.regenerateAsync();
        return ResponseEntity.accepted().build();
    }
}
