package com.accsaber.backend.controller.supporter;

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
import com.accsaber.backend.model.entity.supporter.SupporterAccount;
import com.accsaber.backend.service.supporter.SupporterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Supporters")
public class SupporterPublicController {

    private final SupporterService supporterService;

    @Operation(summary = "Get a user's supporter status (tier, balance, lifetime). Returns an empty state for users who never supported.")
    @GetMapping("/v1/users/{userId}/supporter")
    public ResponseEntity<SupporterAccountResponse> get(@PathVariable Long userId) {
        SupporterAccount account = supporterService.findAccount(userId);
        return ResponseEntity.ok(account == null
                ? SupporterAccountResponse.empty(userId)
                : SupporterAccountResponse.from(account));
    }

    @Operation(summary = "Paginated supporters credits roll. Filter by status: all (default), active, past.")
    @GetMapping("/v1/supporters/credits")
    public ResponseEntity<Page<SupporterCreditsRowResponse>> credits(
            @RequestParam(required = false, defaultValue = "all") String status,
            @PageableDefault(size = 100, sort = "lifetimeSupportedCents", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(supporterService.findCredits(status, pageable)
                .map(SupporterCreditsRowResponse::from));
    }
}
