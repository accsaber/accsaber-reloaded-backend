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

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/og")
@RequiredArgsConstructor
@Tag(name = "Open Graph")
public class OgController {

    private final OgService ogService;

    @GetMapping(value = "/players/{userId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> playerOg(@PathVariable Long userId) {
        return ResponseEntity.ok(ogService.buildPlayerOg(userId));
    }

    @GetMapping(value = "/maps/{mapIdOrCode}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> mapOg(
            @PathVariable String mapIdOrCode,
            @RequestParam(required = false) UUID difficultyId,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String characteristic) {
        return ResponseEntity.ok(ogService.buildMapOg(mapIdOrCode, difficultyId, difficulty, characteristic));
    }

    @GetMapping(value = "/campaigns/{campaignIdOrSlug}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> campaignOg(@PathVariable String campaignIdOrSlug) {
        return ResponseEntity.ok(ogService.buildCampaignOg(campaignIdOrSlug));
    }
}
