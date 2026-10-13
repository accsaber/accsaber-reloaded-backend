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
import com.accsaber.backend.model.dto.request.clan.ChooseClanWarTargetRequest;
import com.accsaber.backend.model.dto.request.clan.OfferClanWarAidRequest;
import com.accsaber.backend.model.dto.request.clan.RequestClanWarAidRequest;
import com.accsaber.backend.model.dto.request.clan.ResolveClanWarAidRequest;
import com.accsaber.backend.model.dto.request.clan.SubmitClanWarPicksRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanWarRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanWarSideRequest;
import com.accsaber.backend.model.dto.response.clan.ClanWarAidResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarDetailResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarHitResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.clan.war.ClanWarCoalitionService;
import com.accsaber.backend.service.clan.war.ClanWarPoolService;
import com.accsaber.backend.service.clan.war.ClanWarRosterService;
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
    private final ClanWarCoalitionService coalitionService;
    private final ClanWarRosterService rosterService;

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
                    + "made get swapped for random maps. The pool locks right away. A war declared as Berserker "
                    + "is an offer: send berserker true to accept it, anything else turns the war into a Duel.")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/wars/{warId}/picks")
    public ResponseEntity<ClanWarDetailResponse> submitPicks(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId,
            @Valid @RequestBody SubmitClanWarPicksRequest request) {
        poolService.submitPicks(warId, principal.getUserId(), request.getMapDifficultyIds(), request.isBerserker());
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

    @Operation(summary = "Ask a clan for help",
            description = "Commander or above on the clan that declared or the clan that got declared on. The other "
                    + "clan has to be your ally, not allied with anyone on the other side, not helping in another war "
                    + "and not pacifist. Clans joining the attack have to sit below the defender in Standing. Duels "
                    + "only.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/wars/{warId}/aid-requests")
    public ResponseEntity<ClanWarAidResponse> requestAid(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId,
            @Valid @RequestBody RequestClanWarAidRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coalitionService.request(warId, principal.getUserId(), request.getClanId()));
    }

    @Operation(summary = "Offer to help in a war",
            description = "Commander or above. Your clan offers to fight for one team, and that team's lead clan has "
                    + "to accept. Same rules as asking for help.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/wars/{warId}/aid-offers")
    public ResponseEntity<ClanWarAidResponse> offerAid(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId,
            @Valid @RequestBody OfferClanWarAidRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coalitionService.offer(warId, principal.getUserId(), request.getTeam()));
    }

    @Operation(summary = "Answer a request or offer",
            description = "The clan that got asked sends accepted or declined. The clan that asked can send "
                    + "cancelled while it is pending. Accepting puts the clan in the war straight away.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/wars/aid/{aidId}")
    public ResponseEntity<ClanWarAidResponse> resolveAid(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID aidId,
            @Valid @RequestBody ResolveClanWarAidRequest request) {
        return ResponseEntity.ok(coalitionService.resolve(aidId, principal.getUserId(), request.getStatus()));
    }

    @Operation(summary = "Requests and offers",
            description = "Newest first. clanId keeps the ones a clan sent, got or leads the war for. warId keeps "
                    + "one war. Filter with status, like pending.")
    @GetMapping("/wars/aid")
    public ResponseEntity<Page<ClanWarAidResponse>> aid(
            @RequestParam(required = false) UUID warId,
            @RequestParam(required = false) UUID clanId,
            @RequestParam(required = false) ClanWarAidStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(coalitionService.list(warId, clanId, status, pageable));
    }

    @Operation(summary = "Leave a war",
            description = "Send left true. Commander or above on a clan that joined to help. The clan's stake left "
                    + "goes to the other team and its players stop fighting. The clans that started the war cannot "
                    + "leave.")
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/wars/{warId}/sides/{clanId}")
    public ResponseEntity<ClanWarResponse> leave(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId,
            @PathVariable UUID clanId,
            @Valid @RequestBody UpdateClanWarSideRequest request) {
        return ResponseEntity.ok(coalitionService.leave(warId, clanId, principal.getUserId()));
    }

    @Operation(summary = "Pick your duel target",
            description = "Any enemy still fighting whose skill is at least 0.8 times yours. Send null to clear it. "
                    + "A play that cannot hit or chip your pick goes to the closest score instead.")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/wars/{warId}/target")
    public ResponseEntity<ClanWarParticipantResponse> target(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId,
            @Valid @RequestBody ChooseClanWarTargetRequest request) {
        return ResponseEntity.ok(rosterService.choose(warId, principal.getUserId(),
                request.getUserId() == null ? null : Long.valueOf(request.getUserId())));
    }

    @Operation(summary = "War participants",
            description = "Players still in first, then by contribution. target is who they picked. inBand tells a "
                    + "signed in viewer whether they can pick that player.")
    @GetMapping("/wars/{warId}/participants")
    public ResponseEntity<Page<ClanWarParticipantResponse>> participants(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID warId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(warService.participants(warId, principal != null ? principal.getUserId() : null,
                pageable));
    }
}
