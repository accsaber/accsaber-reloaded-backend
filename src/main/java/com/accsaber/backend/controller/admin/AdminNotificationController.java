package com.accsaber.backend.controller.admin;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.notification.BroadcastRequest;
import com.accsaber.backend.service.notification.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Send a one-line notification to every active player", description = "Irreversible. Skips players who disabled server notifications. linkTo is an in-app path such as /events/summer-2026.")
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Integer>> broadcast(@Valid @RequestBody BroadcastRequest req) {
        return ResponseEntity.ok(Map.of("delivered",
                notificationService.broadcast(req.getTitle(), req.getLinkTo())));
    }
}
