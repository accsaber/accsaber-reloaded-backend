package com.accsaber.backend.controller.item;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.item.DisintegrateRequest;
import com.accsaber.backend.model.dto.request.item.EquipItemRequest;
import com.accsaber.backend.model.dto.request.item.InventoryFilter;
import com.accsaber.backend.model.dto.request.item.ItemHolderSort;
import com.accsaber.backend.model.dto.request.item.ItemPreviewRequest;
import com.accsaber.backend.model.dto.response.item.DisintegrationResponse;
import com.accsaber.backend.model.dto.response.item.EssenceBalanceResponse;
import com.accsaber.backend.model.dto.response.item.ItemModifierResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.item.ItemTypeResponse;
import com.accsaber.backend.model.dto.response.item.UnusualEffectGroupsResponse;
import com.accsaber.backend.model.dto.response.item.UnusualEffectResponse;
import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.dto.response.statistics.ItemHolderResponse;
import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.item.ItemFileService;
import com.accsaber.backend.service.item.ItemMapper;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.ItemTypeService;
import com.accsaber.backend.service.item.UnusualEffectService;
import com.accsaber.backend.service.stats.SiteStatisticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Items and Market")
public class ItemController {

    private final ItemService itemService;
    private final ItemFileService itemFileService;
    private final ItemTypeService itemTypeService;
    private final UnusualEffectService unusualEffectService;
    private final SiteStatisticsService siteStatisticsService;

    @Operation(summary = "Item types", description = "The type decides the equip slot. Pass its key when "
            + "you equip or clear a slot.")
    @GetMapping("/item-types")
    public ResponseEntity<List<ItemTypeResponse>> listTypes() {
        return ResponseEntity.ok(itemTypeService.findPlayerTypes().stream()
                .map(ItemMapper::toTypeResponse)
                .toList());
    }

    @Operation(summary = "Item modifiers", description = "Rolled on crate opens, per item copy. Not the "
            + "same as score modifiers.")
    @GetMapping("/item-modifiers")
    public ResponseEntity<List<ItemModifierResponse>> listModifiers() {
        return ResponseEntity.ok(itemService.findAllActiveModifiers().stream()
                .map(ItemMapper::toModifierResponse)
                .toList());
    }

    @Operation(summary = "Unusual effects", description = "These sit on one owned copy of an item.")
    @GetMapping("/unusual-effects")
    public ResponseEntity<List<UnusualEffectResponse>> listUnusualEffects() {
        return ResponseEntity.ok(unusualEffectService.findAll(false).stream()
                .map(ItemMapper::toUnusualEffectResponse)
                .toList());
    }

    @Operation(summary = "Unusual effects by crate", description = "Effects with no crate land "
            + "in ungrouped. Hidden crate effects are left out.")
    @GetMapping("/unusual-effects/grouped")
    public ResponseEntity<UnusualEffectGroupsResponse> listUnusualEffectsGrouped() {
        return ResponseEntity.ok(unusualEffectService.findAllGrouped(false));
    }

    @Operation(summary = "Item catalogue", description = "Filter with typeId. Clan cosmetics only show when you ask "
            + "for their type. Hidden items do not show.")
    @GetMapping("/items")
    public ResponseEntity<List<ItemResponse>> listItems(@RequestParam(required = false) UUID typeId) {
        var items = typeId == null
                ? itemService.findPlayerCatalogue()
                : itemService.findByType(typeId, false);
        return ResponseEntity.ok(items.stream().map(ItemMapper::toItemResponse).toList());
    }

    @Operation(summary = "Get one item")
    @GetMapping("/items/{id}")
    public ResponseEntity<ItemResponse> getItem(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.findById(id)));
    }

    @Operation(summary = "Who owns an item", description = "One row per player. A modifier filter only matches "
            + "one copy with all of them. Sort is RECENT, RANK or FOLLOWING, which needs you signed in.")
    @GetMapping("/items/{id}/holders")
    public ResponseEntity<Page<ItemHolderResponse>> getItemHolders(
            @PathVariable UUID id,
            @RequestParam(required = false) List<String> modifier,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "RECENT") ItemHolderSort sort,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        itemService.findById(id);
        Long viewerId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(siteStatisticsService.getItemHolders(id, modifier, search, sort, viewerId, pageable));
    }

    @Operation(summary = "Preview an item combo", description = "Renders an item with a modifier and an "
            + "unusual effect. Nothing is saved.")
    @PostMapping("/items/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'CREATIVE')")
    public ResponseEntity<UserItemResponse> previewItem(@Valid @RequestBody ItemPreviewRequest request) {
        return ResponseEntity.ok(itemService.previewItem(
                request.getItemId(),
                request.getUnusualEffectId(),
                request.getModifierKeys(),
                request.getVariantKey()));
    }

    @Operation(summary = "A player's collection", description = "Flat list, narrow with typeKey. Use the "
            + "inventory route for paging and filters.")
    @GetMapping("/users/{userId}/items")
    public ResponseEntity<List<UserItemResponse>> getUserItems(
            @PathVariable Long userId,
            @RequestParam(required = false) String typeKey) {
        return ResponseEntity.ok(itemService.findUserCollectionHydrated(userId, typeKey));
    }

    @Operation(summary = "What a player has equipped", description = "A map keyed by type.")
    @GetMapping("/users/{userId}/items/equipped")
    public ResponseEntity<Map<String, UserItemResponse>> getEquipped(@PathVariable Long userId) {
        return ResponseEntity.ok(itemService.findEquippedHydrated(userId));
    }

    @Operation(summary = "A player's inventory", description = "Paged, with filters for type, rarity, modifier, "
            + "tradeable, source and deprecated. Most filters take several values. Sorting by crate puts non crate "
            + "items last.")
    @GetMapping("/users/{userId}/inventory")
    public ResponseEntity<Page<UserItemResponse>> getInventory(
            @PathVariable Long userId,
            @RequestParam(required = false) List<String> typeKey,
            @RequestParam(required = false) List<ItemRarity> rarity,
            @RequestParam(required = false) List<String> modifierKey,
            @RequestParam(required = false) Boolean tradeable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<ItemSource> source,
            @RequestParam(required = false) List<UUID> crateItemId,
            @RequestParam(required = false) Boolean deprecated,
            @PageableDefault(size = 50, sort = "awardedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        InventoryFilter filter = new InventoryFilter(typeKey, rarity, modifierKey, tradeable, search, source,
                crateItemId, deprecated);
        return ResponseEntity.ok(itemService.findInventoryHydrated(userId, filter, pageable));
    }

    @Operation(summary = "Crates a player's items came from", description = "Only crates they still own "
            + "something from, sorted by name.")
    @GetMapping("/users/{userId}/inventory/crates")
    public ResponseEntity<List<ItemResponse>> getInventoryCrates(@PathVariable Long userId) {
        return ResponseEntity.ok(itemService.findInventoryCrates(userId));
    }

    @Operation(summary = "Equip an item", description = "The item type picks the slot. Whatever was there comes off "
            + "on its own.")
    @PostMapping("/users/me/items/equip")
    public ResponseEntity<Void> equip(
            @Valid @RequestBody EquipItemRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        itemService.equip(requirePrincipal(principal).getUserId(), request.getLinkId(), request.getVariantKey());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Clear a slot", description = "Pass the type key. The item stays in your inventory.")
    @DeleteMapping("/users/me/items/equip/{typeKey}")
    public ResponseEntity<Void> unequip(
            @PathVariable String typeKey,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        itemService.unequip(requirePrincipal(principal).getUserId(), typeKey);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Download an item's file", description = "You need to own the item. The copy is signed to "
            + "you and will not work for anyone else.")
    @GetMapping("/users/me/items/{linkId}/download")
    public ResponseEntity<byte[]> downloadItemFile(
            @PathVariable UUID linkId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        var file = itemFileService.download(requirePrincipal(principal).getUserId(), linkId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file.bytes());
    }

    @Operation(summary = "Disintegrate items", description = "Turns items into essence. All or nothing. "
            + "This cannot be undone. Confirm with the player first.")
    @PostMapping("/users/me/items/disintegrate")
    public ResponseEntity<DisintegrationResponse> disintegrate(
            @Valid @RequestBody DisintegrateRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(itemService.disintegrate(me, request.getEntries()));
    }

    @Operation(summary = "Your essence", description = "You get essence from disintegrating items and "
            + "spend it on the market.")
    @GetMapping("/users/me/essence")
    public ResponseEntity<EssenceBalanceResponse> getEssenceBalance(
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(EssenceBalanceResponse.builder()
                .balance(itemService.getEssenceBalance(me))
                .reserved(itemService.getReservedEssence(me))
                .build());
    }

    private PlayerUserDetails requirePrincipal(PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        return principal;
    }
}
