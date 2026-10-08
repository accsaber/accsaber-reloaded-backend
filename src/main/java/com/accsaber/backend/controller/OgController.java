package com.accsaber.backend.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.service.og.OgService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/og")
@RequiredArgsConstructor
@Tag(name = "Platform")
public class OgController {

    private final OgService ogService;

    @Operation(summary = "Player link preview", description = "HTML with Open Graph tags for link scrapers "
            + "like Discord. Not JSON.")
    @GetMapping(value = "/players/{userId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> playerOg(@PathVariable Long userId) {
        return ResponseEntity.ok(ogService.buildPlayerOg(userId));
    }

    @Operation(summary = "Map link preview", description = "By id or BeatSaver code. Pass a difficulty id, or "
            + "difficulty plus characteristic, to preview one difficulty.")
    @GetMapping(value = "/maps/{mapIdOrCode}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> mapOg(
            @PathVariable String mapIdOrCode,
            @RequestParam(required = false) UUID difficultyId,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String characteristic) {
        return ResponseEntity.ok(ogService.buildMapOg(mapIdOrCode, difficultyId, difficulty, characteristic));
    }

    @Operation(summary = "Campaign link preview", description = "By id or slug.")
    @GetMapping(value = "/campaigns/{campaignIdOrSlug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> campaignOg(@PathVariable String campaignIdOrSlug) {
        return ResponseEntity.ok(ogService.buildCampaignOg(campaignIdOrSlug));
    }
}
