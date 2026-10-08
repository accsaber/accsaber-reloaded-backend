package com.accsaber.backend.controller.clan;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.clan.CreateClanJoinRequest;
import com.accsaber.backend.model.dto.request.clan.ResolveClanJoinRequest;
import com.accsaber.backend.model.dto.request.clan.TransferClanFounderRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanMemberRequest;
import com.accsaber.backend.model.dto.response.clan.ClanJoinRequestResponse;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.clan.ClanJoinRequestService;
import com.accsaber.backend.service.clan.ClanMembershipService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/clans")
@RequiredArgsConstructor
@Tag(name = "Clans")
public class ClanMemberController {

    private final ClanMembershipService membershipService;
    private final ClanJoinRequestService joinRequestService;

    @Operation(summary = "Clan members",
            description = "Founder first, then by rank, earliest joins first. online means the site is open right "
                    + "now, lastPlayedAt is their newest score.")
    @GetMapping("/{clanId}/members")
    public ResponseEntity<Page<PlayerRef>> members(
            @PathVariable UUID clanId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(membershipService.roster(clanId, pageable));
    }

    @Operation(summary = "Promote or demote someone",
            description = "Commanders handle officers, the founder handles commanders. You can only change lower "
                    + "ranks. Slots come from the clan level.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{clanId}/members/{userId}")
    public ResponseEntity<PlayerRef> changeRole(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateClanMemberRequest request) {
        return ResponseEntity.ok(membershipService.changeRole(clanId, principal.getUserId(), userId,
                request.getRole()));
    }

    @Operation(summary = "Leave or kick someone",
            description = "Your own user ID leaves, anyone else's kicks. Kicks need officer or above and a higher "
                    + "rank. Founders hand the clan over first unless they are the last one.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{clanId}/members/{userId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PathVariable Long userId) {
        membershipService.remove(clanId, principal.getUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Hand over or claim a clan",
            description = "The founder gives the clan to a member and they swap ranks. Your own user ID claims it "
                    + "instead. Only the longest serving commander can, once the founder has gone a year with zero "
                    + "scores.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{clanId}/founder")
    public ResponseEntity<PlayerRef> transferFounder(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody TransferClanFounderRequest request) {
        return ResponseEntity.ok(membershipService.transferFounder(clanId, principal.getUserId(),
                Long.valueOf(request.getUserId())));
    }

    @Operation(summary = "Join or invite",
            description = "No userId asks to join yourself. A userId invites that player, officer and up.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{clanId}/join-requests")
    public ResponseEntity<ClanJoinRequestResponse> createJoinRequest(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody CreateClanJoinRequest request) {
        Long invitedUserId = request.getUserId() != null ? Long.valueOf(request.getUserId()) : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(joinRequestService.create(clanId, principal.getUserId(), invitedUserId));
    }

    @Operation(summary = "Clan's pending requests",
            description = "Officer and up. Has incoming requests and outgoing invites.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{clanId}/join-requests")
    public ResponseEntity<Page<ClanJoinRequestResponse>> clanJoinRequests(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(joinRequestService.forClan(clanId, principal.getUserId(), pageable));
    }

    @Operation(summary = "Your pending requests",
            description = "Invites waiting on you and your unanswered requests.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/join-requests")
    public ResponseEntity<Page<ClanJoinRequestResponse>> myJoinRequests(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(joinRequestService.mine(principal.getUserId(), pageable));
    }

    @Operation(summary = "Answer a join request",
            description = "accepted or declined answers it, cancelled withdraws it. The player answers an invite, an "
                    + "officer answers a request. You cancel your own request, an officer cancels an invite.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/join-requests/{requestId}")
    public ResponseEntity<ClanJoinRequestResponse> resolveJoinRequest(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody ResolveClanJoinRequest request) {
        return ResponseEntity.ok(joinRequestService.resolve(requestId, principal.getUserId(), request.getStatus()));
    }
}
