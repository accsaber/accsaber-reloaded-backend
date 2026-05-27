package com.accsaber.backend.controller.supporter;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.entity.supporter.KofiEvent;
import com.accsaber.backend.service.supporter.SupporterService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v1/webhooks/kofi")
@RequiredArgsConstructor
@Tag(name = "Ko-fi Webhook")
public class KofiWebhookController {

    private final SupporterService supporterService;

    @Value("${accsaber.kofi.verification-token:}")
    private String expectedToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Ko-fi webhook receiver (form-encoded `data` payload)")
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> receive(@RequestParam("data") String data) {
        if (expectedToken == null || expectedToken.isBlank()) {
            log.error("Ko-fi webhook received but accsaber.kofi.verification-token is not configured");
            throw new UnauthorizedException("Webhook verification not configured");
        }

        String suppliedToken;
        try {
            JsonNode parsed = objectMapper.readTree(data);
            JsonNode tokenNode = parsed.get("verification_token");
            suppliedToken = tokenNode == null || tokenNode.isNull() ? null : tokenNode.asText();
        } catch (Exception e) {
            log.warn("Ko-fi webhook payload not valid JSON: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "malformed payload"));
        }

        if (suppliedToken == null || !expectedToken.equals(suppliedToken)) {
            throw new UnauthorizedException("Invalid Ko-fi verification token");
        }

        KofiEvent event = supporterService.recordEvent(data);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "eventId", event.getId(),
                "kofiTransactionId", event.getKofiTransactionId(),
                "claimed", event.getClaimedUser() != null));
    }
}
