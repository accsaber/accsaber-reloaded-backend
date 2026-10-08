package com.accsaber.backend.controller.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@ConditionalOnProperty(name = "accsaber.robots.disallow-all", havingValue = "true")
@Tag(name = "Platform")
public class RobotsController {

    private static final String DISALLOW_ALL = "User-agent: *\nDisallow: /\n";

    @Operation(summary = "Keep crawlers out", description = "Only on environments "
            + "like staging. Production does not serve it.")
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok(DISALLOW_ALL);
    }
}
