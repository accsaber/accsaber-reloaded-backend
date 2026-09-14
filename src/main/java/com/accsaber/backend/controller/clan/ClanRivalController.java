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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.clan.CreateClanRivalRequest;
import com.accsaber.backend.model.dto.response.clan.ClanRivalResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.clan.ClanRivalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/clans")
@RequiredArgsConstructor
@Tag(name = "Clans")
public class ClanRivalController {

    private final ClanRivalService rivalService;

    @Operation(summary = "List a clan's rivals",
            description = "The clans this one has called rivals, most recent first. Pass incoming=true for the clans "
                    + "that have called this one a rival instead.")
    @GetMapping("/{clanId}/rivals")
    public ResponseEntity<Page<ClanRivalResponse>> rivals(
            @PathVariable UUID clanId,
            @RequestParam(defaultValue = "false") boolean incoming,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(rivalService.list(clanId, incoming, pageable));
    }

    @Operation(summary = "Call a clan a rival",
            description = "Commander or above. It is a status and nothing more, and both clans see it in their chat. "
                    + "An ally cannot be a rival, so end the alliance first.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{clanId}/rivals")
    public ResponseEntity<ClanRivalResponse> declare(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody CreateClanRivalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rivalService.declare(clanId, principal.getUserId(), request.getClanId()));
    }

    @Operation(summary = "Drop a rivalry", description = "Commander or above.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{clanId}/rivals/{rivalClanId}")
    public ResponseEntity<Void> drop(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PathVariable UUID rivalClanId) {
        rivalService.drop(clanId, principal.getUserId(), rivalClanId);
        return ResponseEntity.noContent().build();
    }
}
