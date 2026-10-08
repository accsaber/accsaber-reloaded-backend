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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.clan.DeclareClanWarRequest;
import com.accsaber.backend.model.dto.request.clan.OfferClanWarLoanRequest;
import com.accsaber.backend.model.dto.request.clan.ResolveClanWarLoanRequest;
import com.accsaber.backend.model.dto.request.clan.SubmitClanWarPicksRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanWarRequest;
import com.accsaber.backend.model.dto.response.clan.ClanWarDetailResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarHitResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarLoanResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.clan.war.ClanWarLoanService;
import com.accsaber.backend.service.clan.war.ClanWarPoolService;
import com.accsaber.backend.service.clan.war.ClanWarService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/clans")
@RequiredArgsConstructor
@Tag(name = "Clans")
public class ClanWarController {

    private final ClanWarService warService;
    private final ClanWarPoolService poolService;
    private final ClanWarLoanService loanService;

    @Operation(summary = "List clan wars",
            description = "Newest first. clanId filters to one clan, open=true hides finished wars.")
    @GetMapping("/wars")
    public ResponseEntity<Page<ClanWarResponse>> wars(
            @RequestParam(required = false) UUID clanId,
            @RequestParam(defaultValue = "false") boolean open,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(warService.list(clanId, open, pageable));
    }

    @Operation(summary = "Declare war",
            description = "Commander or above, one attack at a time. mapDifficultyIds must match "
                    + "arenaSpec.attackerPicks in count, random arenas take none. No attacking allies or clans too "
                    + "far below your Standing unless they attack you.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{clanId}/wars")
    public ResponseEntity<ClanWarDetailResponse> declare(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody DeclareClanWarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warService.declare(clanId, principal.getUserId(), request));
    }

    @Operation(summary = "Get a war",
            description = "While the defense picks, you only see your own clan's picks. From preparation on everyone "
                    + "sees all.")
    @GetMapping("/wars/{warId}")
    public ResponseEntity<ClanWarDetailResponse> war(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId) {
        return ResponseEntity.ok(warService.get(warId, principal != null ? principal.getUserId() : null));
    }

    @Operation(summary = "Submit defense picks",
            description = "Officer or above on the defense, once, in the pick window. Picks the attacker already "
                    + "made get swapped for random maps. The pool locks right away.")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/wars/{warId}/picks")
    public ResponseEntity<ClanWarDetailResponse> submitPicks(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId,
            @Valid @RequestBody SubmitClanWarPicksRequest request) {
        poolService.submitPicks(warId, principal.getUserId(), request.getMapDifficultyIds());
        return ResponseEntity.ok(warService.get(warId, principal.getUserId()));
    }

    @Operation(summary = "Retreat from a war",
            description = "Send status ended. Only the leading commander or the attacking founder can. Standing "
                    + "already moved stays moved.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/wars/{warId}")
    public ResponseEntity<ClanWarResponse> retreat(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId,
            @Valid @RequestBody UpdateClanWarRequest request) {
        if (request.getStatus() != ClanWarStatus.ended) {
            throw new ValidationException("status", "must be ended");
        }
        return ResponseEntity.ok(warService.retreat(warId, principal.getUserId()));
    }

    @Operation(summary = "War hits",
            description = "Newest first. missingScore hits softer, broke marks the hit that emptied a guard. userId "
                    + "keeps only hits that player dealt or took.")
    @GetMapping("/wars/{warId}/hits")
    public ResponseEntity<Page<ClanWarHitResponse>> hits(
            @PathVariable UUID warId,
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(warService.hits(warId, userId, pageable));
    }

    @Operation(summary = "Lend a player",
            description = "Commander or above on an ally of one side, not in the war. The player must be free and "
                    + "off cooldown, capped by lend slots and Trust Level. They then accept or decline.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/wars/{warId}/loans")
    public ResponseEntity<ClanWarLoanResponse> offerLoan(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId,
            @Valid @RequestBody OfferClanWarLoanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.offer(warId, principal.getUserId(),
                Long.valueOf(request.getUserId()), request.getClanId()));
    }

    @Operation(summary = "Answer a loan",
            description = "The player sends accepted or declined. A lending commander can send cancelled while "
                    + "pending. Accepted players fight until the war ends or they leave.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/wars/loans/{loanId}")
    public ResponseEntity<ClanWarLoanResponse> resolveLoan(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID loanId,
            @Valid @RequestBody ResolveClanWarLoanRequest request) {
        return ResponseEntity.ok(loanService.resolve(loanId, principal.getUserId(), request.getStatus()));
    }

    @Operation(summary = "Your loans",
            description = "Newest first. Filter with status, like pending.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/wars/loans")
    public ResponseEntity<Page<ClanWarLoanResponse>> myLoans(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @RequestParam(required = false) ClanWarLoanStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(loanService.list(null, principal.getUserId(), status, pageable));
    }

    @Operation(summary = "A war's loans",
            description = "Newest first. Filter with status, like pending or accepted.")
    @GetMapping("/wars/{warId}/loans")
    public ResponseEntity<Page<ClanWarLoanResponse>> warLoans(
            @PathVariable UUID warId,
            @RequestParam(required = false) ClanWarLoanStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(loanService.list(warId, null, status, pageable));
    }

    @Operation(summary = "War participants",
            description = "Players still in first, then by contribution. duelTarget is who their duel points at.")
    @GetMapping("/wars/{warId}/participants")
    public ResponseEntity<Page<ClanWarParticipantResponse>> participants(
            @PathVariable UUID warId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(warService.participants(warId, pageable));
    }
}
