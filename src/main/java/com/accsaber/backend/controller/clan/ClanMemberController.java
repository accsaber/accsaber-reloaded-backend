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
import com.accsaber.backend.model.dto.response.clan.ClanMemberResponse;
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

    @Operation(summary = "List a clan's members",
            description = "Founder first, then commanders, officers and members, each rank ordered by who joined "
                    + "earliest. online is whether the player has the site open right now, and lastPlayedAt is "
                    + "their newest score.")
    @GetMapping("/{clanId}/members")
    public ResponseEntity<Page<ClanMemberResponse>> members(
            @PathVariable UUID clanId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(membershipService.roster(clanId, pageable));
    }

    @Operation(summary = "Change a member's rank",
            description = "Commanders move members to and from officer, the founder handles commanders. You can only "
                    + "change someone ranked below you, and officer and commander slots come from the clan level.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{clanId}/members/{userId}")
    public ResponseEntity<ClanMemberResponse> changeRole(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateClanMemberRequest request) {
        return ResponseEntity.ok(membershipService.changeRole(clanId, principal.getUserId(), userId,
                request.getRole()));
    }

    @Operation(summary = "Leave a clan or kick a member",
            description = "Pass your own user id to leave. Anyone else's id is a kick, which needs officer or above "
                    + "and a rank over the player being kicked. A kick clears that player's join cooldown. A founder "
                    + "has to hand the clan over before leaving, unless they are the last one in it.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{clanId}/members/{userId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PathVariable Long userId) {
        membershipService.remove(clanId, principal.getUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Hand over or claim the clan",
            description = "The founder passes the clan to another member, and the two swap ranks. Sending your own "
                    + "user id is a claim instead, which only the longest serving commander can make, and only once "
                    + "the founder has gone without a score for long enough.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{clanId}/founder")
    public ResponseEntity<ClanMemberResponse> transferFounder(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody TransferClanFounderRequest request) {
        return ResponseEntity.ok(membershipService.transferFounder(clanId, principal.getUserId(),
                Long.valueOf(request.getUserId())));
    }

    @Operation(summary = "Ask to join or invite a player",
            description = "Leave userId out to ask to join this clan yourself. Send a userId to invite that player, "
                    + "which needs officer or above.")
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

    @Operation(summary = "List a clan's pending join requests",
            description = "Officer or above. Both the requests players sent in and the invites the clan sent out.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{clanId}/join-requests")
    public ResponseEntity<Page<ClanJoinRequestResponse>> clanJoinRequests(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(joinRequestService.forClan(clanId, principal.getUserId(), pageable));
    }

    @Operation(summary = "List your pending join requests",
            description = "Invites waiting for your answer and the requests you sent that nobody has answered yet.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/join-requests")
    public ResponseEntity<Page<ClanJoinRequestResponse>> myJoinRequests(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(joinRequestService.mine(principal.getUserId(), pageable));
    }

    @Operation(summary = "Answer a join request",
            description = "accepted or declined answers it, cancelled withdraws it. The invited player answers an "
                    + "invite and an officer answers a request. The player who asked cancels their own request, and "
                    + "an officer cancels an invite.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/join-requests/{requestId}")
    public ResponseEntity<ClanJoinRequestResponse> resolveJoinRequest(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody ResolveClanJoinRequest request) {
        return ResponseEntity.ok(joinRequestService.resolve(requestId, principal.getUserId(), request.getStatus()));
    }
}
