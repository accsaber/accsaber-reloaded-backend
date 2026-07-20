package com.accsaber.backend.controller.item;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.accsaber.backend.model.dto.request.item.EquipItemRequest;
import com.accsaber.backend.model.dto.request.item.InventoryFilter;
import com.accsaber.backend.model.dto.request.item.ItemHolderSort;
import com.accsaber.backend.model.dto.request.item.ItemPreviewRequest;
import com.accsaber.backend.model.dto.response.item.DisintegrationResponse;
import com.accsaber.backend.model.dto.response.item.EssenceBalanceResponse;
import com.accsaber.backend.model.dto.response.item.ItemModifierResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.item.ItemTypeResponse;
import com.accsaber.backend.model.dto.response.item.UnusualEffectResponse;
import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.model.dto.response.statistics.ItemHolderResponse;
import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.security.PlayerUserDetails;
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
@Tag(name = "Items")
public class ItemController {

    private final ItemService itemService;
    private final ItemTypeService itemTypeService;
    private final UnusualEffectService unusualEffectService;
    private final SiteStatisticsService siteStatisticsService;

    @Operation(summary = "List all visible item types")
    @GetMapping("/item-types")
    public ResponseEntity<List<ItemTypeResponse>> listTypes() {
        return ResponseEntity.ok(itemTypeService.findAllActive().stream()
                .map(ItemMapper::toTypeResponse)
                .toList());
    }

    @Operation(summary = "List all active item modifiers")
    @GetMapping("/item-modifiers")
    public ResponseEntity<List<ItemModifierResponse>> listModifiers() {
        return ResponseEntity.ok(itemService.findAllActiveModifiers().stream()
                .map(ItemMapper::toModifierResponse)
                .toList());
    }

    @Operation(summary = "List all active unusual effects")
    @GetMapping("/unusual-effects")
    public ResponseEntity<List<UnusualEffectResponse>> listUnusualEffects() {
        return ResponseEntity.ok(unusualEffectService.findAll(false).stream()
                .map(ItemMapper::toUnusualEffectResponse)
                .toList());
    }

    @Operation(summary = "List all visible items, optionally filtered by type")
    @GetMapping("/items")
    public ResponseEntity<List<ItemResponse>> listItems(@RequestParam(required = false) UUID typeId) {
        var items = typeId == null
                ? itemService.findAllVisible()
                : itemService.findByType(typeId, false);
        return ResponseEntity.ok(items.stream().map(ItemMapper::toItemResponse).toList());
    }

    @Operation(summary = "Get an item by id")
    @GetMapping("/items/{id}")
    public ResponseEntity<ItemResponse> getItem(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.findById(id)));
    }

    @Operation(summary = "List the holders of an item", description = "Players who own the given item, aggregated one row per holder."
            + " Filter by modifier keys (a holder qualifies only via instances carrying ALL requested modifiers) and by holder"
            + " name search. Sort by RECENT (most recently acquired), RANK (best overall AccSaber rank), or FOLLOWING"
            + " (players the authenticated viewer follows first — requires login).")
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

    @Operation(summary = "Preview an item, modifier, and unusual effect combo exactly as it renders equipped")
    @PostMapping("/items/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'CREATIVE')")
    public ResponseEntity<UserItemResponse> previewItem(@Valid @RequestBody ItemPreviewRequest request) {
        return ResponseEntity.ok(itemService.previewItem(
                request.getItemId(),
                request.getUnusualEffectId(),
                request.getModifierKeys(),
                request.getVariantKey()));
    }

    @Operation(summary = "List a user's owned item collection")
    @GetMapping("/users/{userId}/items")
    public ResponseEntity<List<UserItemResponse>> getUserItems(
            @PathVariable Long userId,
            @RequestParam(required = false) String typeKey) {
        var links = typeKey == null
                ? itemService.findUserCollection(userId)
                : itemService.findUserCollectionByType(userId, typeKey);
        return ResponseEntity.ok(links.stream().map(ItemMapper::toUserItemResponse).toList());
    }

    @Operation(summary = "Get a user's equipped items, keyed by type key")
    @GetMapping("/users/{userId}/items/equipped")
    public ResponseEntity<Map<String, UserItemResponse>> getEquipped(@PathVariable Long userId) {
        return ResponseEntity.ok(itemService.findEquippedHydrated(userId));
    }

    @Operation(summary = "Paginated user inventory with filtering and sorting")
    @GetMapping("/users/{userId}/inventory")
    public ResponseEntity<Page<UserItemResponse>> getInventory(
            @PathVariable Long userId,
            @RequestParam(required = false) List<String> typeKey,
            @RequestParam(required = false) List<ItemRarity> rarity,
            @RequestParam(required = false) List<String> modifierKey,
            @RequestParam(required = false) Boolean tradeable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<ItemSource> source,
            @RequestParam(required = false) Boolean deprecated,
            @PageableDefault(size = 50, sort = "awardedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        InventoryFilter filter = new InventoryFilter(typeKey, rarity, modifierKey, tradeable, search, source,
                deprecated);
        return ResponseEntity.ok(itemService.findInventory(userId, filter, pageable)
                .map(ItemMapper::toUserItemResponse));
    }

    @Operation(summary = "Equip an owned item link to its corresponding profile slot")
    @PostMapping("/users/me/items/equip")
    public ResponseEntity<Void> equip(
            @Valid @RequestBody EquipItemRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        itemService.equip(requirePrincipal(principal).getUserId(), request.getLinkId(), request.getVariantKey());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Clear the equipped item for a given item type")
    @DeleteMapping("/users/me/items/equip/{typeKey}")
    public ResponseEntity<Void> unequip(
            @PathVariable String typeKey,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        itemService.unequip(requirePrincipal(principal).getUserId(), typeKey);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Disintegrate an owned item link into item essence (destroys it for its worth)")
    @PostMapping("/users/me/items/{linkId}/disintegrate")
    public ResponseEntity<DisintegrationResponse> disintegrate(
            @PathVariable UUID linkId,
            @RequestParam(required = false) Long quantity,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(itemService.disintegrate(me, linkId, quantity));
    }

    @Operation(summary = "Get my current item essence balance")
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
