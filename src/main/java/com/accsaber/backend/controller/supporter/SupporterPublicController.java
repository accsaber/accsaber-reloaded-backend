package com.accsaber.backend.controller.supporter;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.supporter.SupporterAccountResponse;
import com.accsaber.backend.model.dto.response.supporter.SupporterCreditsRowResponse;
import com.accsaber.backend.model.dto.response.supporter.SupporterTierResponse;
import com.accsaber.backend.model.entity.supporter.SupporterAccount;
import com.accsaber.backend.service.supporter.SupporterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Players")
public class SupporterPublicController {

    private final SupporterService supporterService;

    @Operation(summary = "A player's supporter status", description = "Tier, balance and lifetime total. Non "
            + "supporters get an empty response, not a 404.")
    @GetMapping("/v1/users/{userId}/supporter")
    public ResponseEntity<SupporterAccountResponse> get(@PathVariable Long userId) {
        SupporterAccount account = supporterService.findAccount(userId);
        return ResponseEntity.ok(account == null
                ? SupporterAccountResponse.empty(userId)
                : SupporterAccountResponse.from(account));
    }

    @Operation(summary = "Supporter tiers", description = "Cheapest first, with the monthly price.")
    @GetMapping("/v1/supporters/tiers")
    public ResponseEntity<List<SupporterTierResponse>> tiers() {
        return ResponseEntity.ok(supporterService.findTiers().stream()
                .map(SupporterTierResponse::from)
                .toList());
    }

    @Operation(summary = "Supporter credits", description = "Everyone who has supported AccSaber. Filter "
            + "status with all, active or past.")
    @GetMapping("/v1/supporters/credits")
    public ResponseEntity<Page<SupporterCreditsRowResponse>> credits(
            @RequestParam(required = false, defaultValue = "all") String status,
            @PageableDefault(size = 100, sort = "lifetimeSupportedCents", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(supporterService.findCredits(status, pageable)
                .map(SupporterCreditsRowResponse::from));
    }
}
