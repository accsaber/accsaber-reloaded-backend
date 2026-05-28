package com.accsaber.backend.controller.admin;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.supporter.ManualSupporterGrantRequest;
import com.accsaber.backend.model.entity.supporter.KofiEvent;
import com.accsaber.backend.model.entity.supporter.KofiEventType;
import com.accsaber.backend.service.supporter.SupporterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/supporters")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Supporters")
public class AdminSupporterController {

    private final SupporterService supporterService;

    @Operation(summary = "Manually grant supporter status to a user (synthesizes a claimed Ko-fi event and applies the tier + items)")
    @PostMapping("/grant")
    public ResponseEntity<Map<String, Object>> grant(@Valid @RequestBody ManualSupporterGrantRequest request) {
        KofiEventType type = parseType(request.getType());
        KofiEvent event = supporterService.grantManually(
                request.getUserId(),
                request.getAmountCents(),
                request.getTierName(),
                type,
                request.getFromName(),
                request.getEmail(),
                request.getNote());
        return ResponseEntity.ok(Map.of(
                "kofiTransactionId", event.getKofiTransactionId(),
                "userId", request.getUserId(),
                "tier", event.getTierName(),
                "amountCents", event.getAmountCents(),
                "type", event.getType().name()));
    }

    private KofiEventType parseType(String raw) {
        if (raw == null || raw.isBlank()) return KofiEventType.donation;
        try {
            return KofiEventType.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            return KofiEventType.fromKofiPayload(raw);
        }
    }
}
