package com.accsaber.backend.controller.notification;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.response.notification.NotificationResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.notification.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "List my notifications", description = "Newest first. Pass unreadOnly=true for the unread feed.")
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 30) Pageable pageable,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(notificationService.findFeed(me, unreadOnly, pageable));
    }

    @Operation(summary = "Count my unread notifications", description = "Cheap endpoint for the badge.")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(me)));
    }

    @Operation(summary = "Mark one notification as read")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        notificationService.markRead(id, me);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark every notification as read")
    @PatchMapping("/read")
    public ResponseEntity<Map<String, Integer>> markAllRead(
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllRead(me)));
    }

    @Operation(summary = "Delete all of my notifications")
    @DeleteMapping
    public ResponseEntity<Void> clearAll(@AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        notificationService.clearAll(me);
        return ResponseEntity.noContent().build();
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
