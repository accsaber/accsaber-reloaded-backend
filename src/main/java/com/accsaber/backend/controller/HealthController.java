package com.accsaber.backend.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v1/public")
@Tag(name = "Health")
public class HealthController {

    @Operation(summary = "Ping the service", description = "Returns service status and current timestamp")
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        log.info("Ping received");
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "timestamp", Instant.now(),
                "service", "accsaber-backend"));
    }
}
