package com.accsaber.backend.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.service.og.OgService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/og")
@RequiredArgsConstructor
public class OgController {

    private final OgService ogService;

    @GetMapping(value = "/players/{userId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> playerOg(@PathVariable Long userId) {
        return ResponseEntity.ok(ogService.buildPlayerOg(userId));
    }

    @GetMapping(value = "/maps/{mapId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> mapOg(@PathVariable UUID mapId, @RequestParam(required = false) UUID difficultyId) {
        return ResponseEntity.ok(ogService.buildMapOg(mapId, difficultyId));
    }
}
