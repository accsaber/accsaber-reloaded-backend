package com.accsaber.backend.controller.admin;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.supporter.ClaimSupporterEventRequest;
import com.accsaber.backend.model.dto.request.supporter.ManualSupporterGrantRequest;
import com.accsaber.backend.model.dto.response.supporter.KofiEventResponse;
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
@Tag(name = "Admin - Players")
public class AdminSupporterController {

    private final SupporterService supporterService;

    @Operation(summary = "Give someone supporter")
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
                "userId", String.valueOf(request.getUserId()),
                "tier", event.getTierName(),
                "amountCents", event.getAmountCents(),
                "type", event.getType().name()));
    }

    @Operation(summary = "List Ko-fi events", description = "Newest first. status is all, unclaimed or claimed. "
            + "search matches donor email and name.")
    @GetMapping("/events")
    public ResponseEntity<Page<KofiEventResponse>> events(
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(supporterService.findEvents(status, userId, search, pageable)
                .map(KofiEventResponse::from));
    }

    @Operation(summary = "Claim a Ko-fi event for someone")
    @PostMapping("/claim")
    public ResponseEntity<Void> claim(@Valid @RequestBody ClaimSupporterEventRequest request) {
        supporterService.claimByAdmin(request.getKofiTransactionId(), request.getUserId());
        return ResponseEntity.noContent().build();
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
