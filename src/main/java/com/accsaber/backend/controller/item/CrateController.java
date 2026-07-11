package com.accsaber.backend.controller.item;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.response.item.CrateContentResponse;
import com.accsaber.backend.model.dto.response.item.CrateModifierResponse;
import com.accsaber.backend.model.dto.response.item.CrateOpenResponse;
import com.accsaber.backend.model.dto.response.item.UnusualEffectResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.item.CrateService;
import com.accsaber.backend.service.item.ItemMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Crates")
public class CrateController {

    private final CrateService crateService;

    @Operation(summary = "List a crate's reward pool with normalized drop chances")
    @GetMapping("/crates/{crateItemId}/contents")
    public ResponseEntity<List<CrateContentResponse>> listContents(@PathVariable UUID crateItemId) {
        return ResponseEntity.ok(ItemMapper.toCrateContentResponses(crateService.listVisibleContents(crateItemId)));
    }

    @Operation(summary = "List the modifiers a crate can roll onto its reward, with drop chances")
    @GetMapping("/crates/{crateItemId}/modifiers")
    public ResponseEntity<List<CrateModifierResponse>> listModifiers(@PathVariable UUID crateItemId) {
        return ResponseEntity.ok(ItemMapper.toCrateModifierResponses(crateService.listModifiers(crateItemId)));
    }

    @Operation(summary = "List the unusual effects a crate can roll (equal chance among them)")
    @GetMapping("/crates/{crateItemId}/unusual-effects")
    public ResponseEntity<List<UnusualEffectResponse>> listUnusualEffects(@PathVariable UUID crateItemId) {
        return ResponseEntity.ok(crateService.listUnusualEffects(crateItemId).stream()
                .map(ItemMapper::toUnusualEffectResponse)
                .toList());
    }

    @Operation(summary = "Open one of my owned crate item links and receive a random reward")
    @PostMapping("/users/me/crates/{linkId}/open")
    public ResponseEntity<CrateOpenResponse> open(
            @PathVariable UUID linkId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(ItemMapper.toCrateOpenResponse(crateService.openCrate(me, linkId)));
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
