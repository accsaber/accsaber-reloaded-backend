package com.accsaber.backend.controller.admin;

import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.websocket.WebSocketConnectionManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/ws")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin WebSocket")
public class AdminWebSocketController {

    private static final Set<String> VALID_PLATFORMS = Set.of("beatleader", "scoresaber");

    private final WebSocketConnectionManager webSocketConnectionManager;

    @Operation(summary = "Reconnect a WebSocket")
    @PostMapping("/reconnect")
    public ResponseEntity<Void> reconnectWebSocket(@RequestParam String platform) {
        if (!VALID_PLATFORMS.contains(platform.toLowerCase())) {
            throw new ValidationException("Unknown platform: " + platform + ". Valid values: beatleader, scoresaber");
        }
        webSocketConnectionManager.reconnect(platform);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get WebSocket connection status")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getWebSocketStatus() {
        return ResponseEntity.ok(webSocketConnectionManager.getStatus());
    }
}
