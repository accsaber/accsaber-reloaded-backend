package com.accsaber.backend.controller.supporter;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.service.supporter.SupporterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/webhooks/kofi")
@RequiredArgsConstructor
@Tag(name = "Platform")
public class KofiWebhookController {

    private final SupporterService supporterService;

    @Operation(summary = "Ko-fi webhook", description = "For Ko-fi only. Auth is the verification token in the "
            + "payload.")
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> receive(@RequestParam String data) {
        return supporterService.receiveWebhook(data)
                .map(event -> ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.<String, Object>of(
                        "eventId", event.getId(),
                        "kofiTransactionId", event.getKofiTransactionId(),
                        "claimed", event.getClaimedUser() != null)))
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.of("error", "malformed payload")));
    }
}
