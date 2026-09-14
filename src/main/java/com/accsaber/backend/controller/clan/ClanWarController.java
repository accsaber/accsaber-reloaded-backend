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
import com.accsaber.backend.model.dto.request.clan.SubmitClanWarPicksRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanWarRequest;
import com.accsaber.backend.model.dto.response.clan.ClanWarDetailResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.security.PlayerUserDetails;
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

    @Operation(summary = "List clan wars",
            description = "Newest first. Pass clanId for one clan's wars on either side, and open=true to leave out the "
                    + "ones that are over.")
    @GetMapping("/wars")
    public ResponseEntity<Page<ClanWarResponse>> wars(
            @RequestParam(required = false) UUID clanId,
            @RequestParam(defaultValue = "false") boolean open,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(warService.list(clanId, open, pageable));
    }

    @Operation(summary = "Declare war",
            description = "Commander or above, and your clan can only be attacking one clan at a time. The arena and "
                    + "ruleset have to be unlocked at your level, turf wars need their category or complexity range, and "
                    + "mapDifficultyIds are your picks, which must be exactly as many as arenaSpec.attackerPicks comes "
                    + "back as. A random arena takes none. You cannot attack an ally, or a clan whose Standing is too far "
                    + "below yours unless they are attacking you.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{clanId}/wars")
    public ResponseEntity<ClanWarDetailResponse> declare(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody DeclareClanWarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warService.declare(clanId, principal.getUserId(), request));
    }

    @Operation(summary = "Get a clan war",
            description = "The war with both sides and its pool. While the defense is still picking, the pool only shows "
                    + "the picks of your own clan, and from preparation on everyone sees all of it.")
    @GetMapping("/wars/{warId}")
    public ResponseEntity<ClanWarDetailResponse> war(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId) {
        return ResponseEntity.ok(warService.get(warId, principal != null ? principal.getUserId() : null));
    }

    @Operation(summary = "Submit the defense picks",
            description = "Officer or above on the defending clan, once, inside the pick window. Any pick the attacker "
                    + "already made gets swapped for a random legal map, and the pool locks straight away.")
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
            description = "Send status ended. Only the commander leading the attack or the attacking founder can pull "
                    + "out, and Standing that already moved stays where it went.")
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

    @Operation(summary = "List a war's participants",
            description = "Everyone enrolled once the war went active, players still in it first, then by contribution. "
                    + "duelTarget is who a duel points them at.")
    @GetMapping("/wars/{warId}/participants")
    public ResponseEntity<Page<ClanWarParticipantResponse>> participants(
            @PathVariable UUID warId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(warService.participants(warId, pageable));
    }
}
