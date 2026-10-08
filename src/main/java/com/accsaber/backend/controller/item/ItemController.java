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

    @Operation(summary = "List the item types", description = "The kinds of item a player can own, which is also what decides the slot "
            + "an item goes in when it is equipped. Each type has a key, and that key is what you pass when equipping or "
            + "clearing a slot.")
    @GetMapping("/item-types")
    public ResponseEntity<List<ItemTypeResponse>> listTypes() {
        return ResponseEntity.ok(itemTypeService.findPlayerTypes().stream()
                .map(ItemMapper::toTypeResponse)
                .toList());
    }

    @Operation(summary = "List the item modifiers", description = "Modifiers are the extra flourish an item instance can carry, "
            + "rolled when a crate is opened. Two players can hold the same item and have it look different because of these. "
            + "Not to be confused with score modifiers, which are a separate thing entirely.")
    @GetMapping("/item-modifiers")
    public ResponseEntity<List<ItemModifierResponse>> listModifiers() {
        return ResponseEntity.ok(itemService.findAllActiveModifiers().stream()
                .map(ItemMapper::toModifierResponse)
                .toList());
    }

    @Operation(summary = "List the unusual effects", description = "Rarer visual effects that sit on a single item instance "
            + "rather than on the item itself, so they belong to one specific copy someone owns.")
    @GetMapping("/unusual-effects")
    public ResponseEntity<List<UnusualEffectResponse>> listUnusualEffects() {
        return ResponseEntity.ok(unusualEffectService.findAll(false).stream()
                .map(ItemMapper::toUnusualEffectResponse)
                .toList());
    }

    @Operation(summary = "List unusual effects by which crate drops them", description = "The same effects but arranged under "
            + "the crate they come from, which is the shape you want for a collection screen. An effect that drops from "
            + "several crates turns up under each of them, and anything attached to no crate at all lands in ungrouped. "
            + "Effects that only come from a hidden crate are left out entirely, so this is not a complete list of what "
            + "exists.")
    @GetMapping("/unusual-effects/grouped")
    public ResponseEntity<UnusualEffectGroupsResponse> listUnusualEffectsGrouped() {
        return ResponseEntity.ok(unusualEffectService.findAllGrouped(false));
    }

    @Operation(summary = "List the items", description = "The player item catalogue, narrowed to one type if you pass typeId. Clan cosmetics are left out unless you ask for one of their types by typeId. Only "
            + "items marked visible show up, so anything being held back for a future release will not appear here even "
            + "though it exists.")
    @GetMapping("/items")
    public ResponseEntity<List<ItemResponse>> listItems(@RequestParam(required = false) UUID typeId) {
        var items = typeId == null
                ? itemService.findPlayerCatalogue()
                : itemService.findByType(typeId, false);
        return ResponseEntity.ok(items.stream().map(ItemMapper::toItemResponse).toList());
    }

    @Operation(summary = "Get one item", description = "A single item from the catalogue, with its type, rarity and worth.")
    @GetMapping("/items/{id}")
    public ResponseEntity<ItemResponse> getItem(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.findById(id)));
    }

    @Operation(summary = "List who owns an item", description = "The players holding an item, one row each however many copies "
            + "they have. You can filter by modifier, though a holder only counts if they have a single copy carrying all the "
            + "modifiers you asked for rather than spread across several. Search by name as well if you need. Sort is RECENT "
            + "for most recently picked up, RANK for best overall AccSaber rank, or FOLLOWING to put people you follow first, "
            + "and that last one needs you to be signed in.")
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

    @Operation(summary = "Preview an item combination", description = "Renders an item with a modifier and an unusual effect "
            + "exactly as it would look equipped, without anyone having to own it. Nothing is created or saved, it just gives "
            + "you the rendered shape back, so it is safe to call as often as you like while trying combinations out.")
    @PostMapping("/items/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'CREATIVE')")
    public ResponseEntity<UserItemResponse> previewItem(@Valid @RequestBody ItemPreviewRequest request) {
        return ResponseEntity.ok(itemService.previewItem(
                request.getItemId(),
                request.getUnusualEffectId(),
                request.getModifierKeys(),
                request.getVariantKey()));
    }

    @Operation(summary = "Get a player's collection", description = "Everything a player owns, as a flat list. Pass typeKey to "
            + "narrow it to one kind. If you want paging and proper filtering, the inventory route below is the better one.")
    @GetMapping("/users/{userId}/items")
    public ResponseEntity<List<UserItemResponse>> getUserItems(
            @PathVariable Long userId,
            @RequestParam(required = false) String typeKey) {
        return ResponseEntity.ok(itemService.findUserCollectionHydrated(userId, typeKey));
    }

    @Operation(summary = "Get what a player has equipped", description = "The items a player is currently showing, as a map "
            + "keyed by type so you can look up a slot directly instead of searching a list. This is what you want for "
            + "rendering someone's profile.")
    @GetMapping("/users/{userId}/items/equipped")
    public ResponseEntity<Map<String, UserItemResponse>> getEquipped(@PathVariable Long userId) {
        return ResponseEntity.ok(itemService.findEquippedHydrated(userId));
    }

    @Operation(summary = "Get a player's inventory", description = "The same collection but paged and with a lot more to filter "
            + "on, which is what an inventory screen wants. Narrow by type, rarity, modifier, whether something can be traded, "
            + "where it came from, or whether it has been deprecated, and search by name. Most of the list filters take "
            + "several values at once. crateItemId narrows to the items that dropped out of particular crates. Sort takes the "
            + "usual link fields plus rarity and crate, where crate orders by the name of the crate an item dropped from and "
            + "leaves everything that did not come from a crate at the end.")
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

    @Operation(summary = "List the crates a player's items came from", description = "Every crate that dropped something the "
            + "player still owns, sorted by name. This is what fills the collection picker on the inventory screen, so it only "
            + "lists crates they actually have something from rather than the whole catalogue.")
    @GetMapping("/users/{userId}/inventory/crates")
    public ResponseEntity<List<ItemResponse>> getInventoryCrates(@PathVariable Long userId) {
        return ResponseEntity.ok(itemService.findInventoryCrates(userId));
    }

    @Operation(summary = "Equip an item", description = "Puts one of your items into its slot, which is decided by the item's "
            + "type rather than by you. Whatever was in that slot before comes off automatically, so there is no need to "
            + "unequip first.")
    @PostMapping("/users/me/items/equip")
    public ResponseEntity<Void> equip(
            @Valid @RequestBody EquipItemRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        itemService.equip(requirePrincipal(principal).getUserId(), request.getLinkId(), request.getVariantKey());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Clear a slot", description = "Takes off whatever you have equipped in one slot, addressed by the type "
            + "key rather than by the item. The item stays in your inventory, it just stops being shown.")
    @DeleteMapping("/users/me/items/equip/{typeKey}")
    public ResponseEntity<Void> unequip(
            @PathVariable String typeKey,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        itemService.unequip(requirePrincipal(principal).getUserId(), typeKey);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Download an item's file", description = "Some items come with a file attached, and this hands you "
            + "yours. The copy you get is signed to you specifically, so please do not pass it around expecting it to work for "
            + "someone else. You have to own the item to get anything back.")
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

    @Operation(summary = "Disintegrate items for essence", description = "Destroys things you own and gives you essence worth "
            + "their value instead. Send as many entries as you like in one call, each with a quantity if you are holding a "
            + "stack and only want to break some of it. It is all or nothing, so if one entry is not allowed nothing is "
            + "destroyed and you get told why. This one does not come back, so make sure the player meant it before you call "
            + "it.")
    @PostMapping("/users/me/items/disintegrate")
    public ResponseEntity<DisintegrationResponse> disintegrate(
            @Valid @RequestBody DisintegrateRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        Long me = requirePrincipal(principal).getUserId();
        return ResponseEntity.ok(itemService.disintegrate(me, request.getEntries()));
    }

    @Operation(summary = "Get your essence balance", description = "How much item essence you are holding. Essence comes from "
            + "disintegrating items and is what you spend on the market.")
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
