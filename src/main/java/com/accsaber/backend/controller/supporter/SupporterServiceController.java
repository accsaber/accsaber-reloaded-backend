package com.accsaber.backend.controller.supporter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.supporter.AssignSupporterEventRequest;
import com.accsaber.backend.model.dto.request.supporter.ClaimByRoleSignalRequest;
import com.accsaber.backend.model.entity.supporter.KofiEvent;
import com.accsaber.backend.service.supporter.SupporterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/supporters")
@RequiredArgsConstructor
@Tag(name = "Supporters (Service)")
@PreAuthorize("hasRole('SERVICE')")
public class SupporterServiceController {

    private final SupporterService supporterService;

    @Operation(summary = "Bot signal: a Ko-fi supporter role was just assigned in Discord - correlate with an unclaimed webhook event")
    @PostMapping("/claim-by-role")
    public ResponseEntity<Map<String, Object>> claimByRole(@Valid @RequestBody ClaimByRoleSignalRequest request) {
        Instant when = request.getAssignedAt() != null ? request.getAssignedAt() : Instant.now();
        Optional<KofiEvent> claimed = supporterService.claimByRoleSignalForDiscord(
                request.getDiscordId(), request.getTierName(), when);
        return ResponseEntity.ok(Map.of(
                "matched", claimed.isPresent(),
                "kofiTransactionId", claimed.map(KofiEvent::getKofiTransactionId).orElse("")));
    }

    @Operation(summary = "Admin fallback (via bot `/assign` command): claim a specific Ko-fi event for a Discord-linked user")
    @PostMapping("/assign")
    public ResponseEntity<Void> assign(@Valid @RequestBody AssignSupporterEventRequest request) {
        supporterService.claimByAdminForDiscord(request.getKofiTransactionId(), request.getDiscordId());
        return ResponseEntity.noContent().build();
    }
}
