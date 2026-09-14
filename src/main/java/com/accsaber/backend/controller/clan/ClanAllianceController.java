package com.accsaber.backend.controller.clan;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.clan.CreateClanAllianceRequest;
import com.accsaber.backend.model.dto.request.clan.ResolveClanAllianceRequest;
import com.accsaber.backend.model.dto.response.clan.ClanAllianceResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.clan.ClanAllianceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/clans")
@RequiredArgsConstructor
@Tag(name = "Clans")
public class ClanAllianceController {

    private final ClanAllianceService allianceService;

    @Operation(summary = "List a clan's allies",
            description = "Every active alliance, newest first, with the other clan as ally. trust carries the Trust "
                    + "Level, how many players that alliance can have out on loan at once, and the contribution lent "
                    + "players have put up across it.")
    @GetMapping("/{clanId}/alliances")
    public ResponseEntity<Page<ClanAllianceResponse>> alliances(
            @PathVariable UUID clanId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(allianceService.list(clanId, pageable));
    }

    @Operation(summary = "List a clan's pending alliance proposals",
            description = "Founder only. Both the proposals this clan sent and the ones waiting for its answer, "
                    + "told apart by incoming.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{clanId}/alliances/proposals")
    public ResponseEntity<Page<ClanAllianceResponse>> proposals(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(allianceService.proposals(clanId, principal.getUserId(), pageable));
    }

    @Operation(summary = "Propose an alliance",
            description = "Founder only, and your clan needs a free ally slot. The other clan's founder answers it.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{clanId}/alliances")
    public ResponseEntity<ClanAllianceResponse> propose(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody CreateClanAllianceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(allianceService.propose(clanId, principal.getUserId(), request.getClanId()));
    }

    @Operation(summary = "Answer or end an alliance",
            description = "active accepts a proposal, which only the clan that received it can do and which needs a "
                    + "free ally slot on both sides. declined turns a proposal down or withdraws your own, and ended "
                    + "breaks an active alliance. Founders of either clan only.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/alliances/{allianceId}")
    public ResponseEntity<ClanAllianceResponse> resolve(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID allianceId,
            @Valid @RequestBody ResolveClanAllianceRequest request) {
        return ResponseEntity.ok(allianceService.resolve(allianceId, principal.getUserId(), request.getStatus()));
    }
}
