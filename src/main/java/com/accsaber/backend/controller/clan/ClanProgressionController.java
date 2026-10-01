package com.accsaber.backend.controller.clan;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.clan.EquipClanItemRequest;
import com.accsaber.backend.model.dto.response.clan.ClanItemResponse;
import com.accsaber.backend.model.dto.response.clan.ClanLevelResponse;
import com.accsaber.backend.model.dto.response.clan.ClanLevelStepResponse;
import com.accsaber.backend.model.dto.response.clan.ClanXpGrantResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.clan.ClanCosmeticService;
import com.accsaber.backend.service.clan.ClanLevelService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/clans")
@RequiredArgsConstructor
@Tag(name = "Clans")
public class ClanProgressionController {

    private final ClanLevelService levelService;
    private final ClanCosmeticService cosmeticService;

    @Operation(summary = "List what every clan level unlocks",
            description = "One step per level that unlocks something, with the total clan XP needed to reach it. "
                    + "Capacities on a step are what that level adds on top of the levels before it.")
    @GetMapping("/levels")
    public ResponseEntity<List<ClanLevelStepResponse>> levels() {
        return ResponseEntity.ok(levelService.table());
    }

    @Operation(summary = "Get a clan's level",
            description = "Progress through the current level, plus everything unlocked so far added together.")
    @GetMapping("/{clanId}/level")
    public ResponseEntity<ClanLevelResponse> level(@PathVariable UUID clanId) {
        return ResponseEntity.ok(levelService.level(clanId));
    }

    @Operation(summary = "List a clan's XP history",
            description = "Every XP grant the clan banked, newest first. rawAmount is what the source paid, and amount "
                    + "is what landed after dividing by the roster factor at the time.")
    @GetMapping("/{clanId}/xp")
    public ResponseEntity<Page<ClanXpGrantResponse>> xp(
            @PathVariable UUID clanId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(levelService.xpHistory(clanId, pageable));
    }

    @Operation(summary = "List a clan's cosmetics", description = "Every cosmetic the clan owns and whether it is equipped.")
    @GetMapping("/{clanId}/items")
    public ResponseEntity<Page<ClanItemResponse>> items(
            @PathVariable UUID clanId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(cosmeticService.owned(clanId, pageable));
    }

    @Operation(summary = "Equip a clan cosmetic",
            description = "Founder only. Puts an owned cosmetic in the slot for its type, replacing whatever was there, "
                    + "and returns everything the clan now has equipped.")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{clanId}/equipped")
    public ResponseEntity<List<ItemResponse>> equip(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @Valid @RequestBody EquipClanItemRequest request) {
        return ResponseEntity.ok(cosmeticService.equip(clanId, principal.getUserId(), request.getItemId()));
    }

    @Operation(summary = "Clear a clan cosmetic slot", description = "Founder only. Empties the slot for that item type key.")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{clanId}/equipped/{itemTypeKey}")
    public ResponseEntity<Void> unequip(
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PathVariable UUID clanId,
            @PathVariable String itemTypeKey) {
        cosmeticService.unequip(clanId, principal.getUserId(), itemTypeKey);
        return ResponseEntity.noContent().build();
    }
}
