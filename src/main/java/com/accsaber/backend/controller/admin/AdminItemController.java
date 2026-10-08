package com.accsaber.backend.controller.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.model.dto.request.item.AwardItemRequest;
import com.accsaber.backend.model.dto.request.item.CreateItemRequest;
import com.accsaber.backend.model.dto.request.item.CreateItemTypeRequest;
import com.accsaber.backend.model.dto.request.item.UpdateItemModifierRequest;
import com.accsaber.backend.model.dto.request.item.UpdateItemRequest;
import com.accsaber.backend.model.dto.request.item.UpdateItemTypeRequest;
import com.accsaber.backend.model.dto.response.item.ItemModifierResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.item.ItemTypeResponse;
import com.accsaber.backend.model.dto.response.item.UserItemResponse;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.item.ItemMapper;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.ItemTypeService;
import com.accsaber.backend.service.media.MediaProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Items and Crates")
public class AdminItemController {

    private static final String ITEM_ICON_SUBDIR = "items";

    private final ItemService itemService;
    private final ItemTypeService itemTypeService;
    private final MediaProcessingService mediaProcessingService;

    @Operation(summary = "List item types")
    @GetMapping("/item-types")
    public ResponseEntity<List<ItemTypeResponse>> listTypes(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var types = includeInactive ? itemTypeService.findAll() : itemTypeService.findAllActive();
        return ResponseEntity.ok(types.stream().map(ItemMapper::toTypeResponse).toList());
    }

    @Operation(summary = "All items")
    @PreAuthorize("hasAnyRole('ADMIN', 'CREATIVE')")
    @GetMapping("/items")
    public ResponseEntity<List<ItemResponse>> listItems(
            @RequestParam(required = false) UUID typeId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var items = typeId == null
                ? itemService.findAllForStaff(includeInactive)
                : itemService.findByTypeForStaff(typeId, includeInactive);
        return ResponseEntity.ok(items.stream().map(ItemMapper::toItemResponse).toList());
    }

    @Operation(summary = "Get an item")
    @GetMapping("/items/{id}")
    public ResponseEntity<ItemResponse> getItem(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.findByIdForStaff(id)));
    }

    @Operation(summary = "New item type")
    @PostMapping("/item-types")
    public ResponseEntity<ItemTypeResponse> createType(@Valid @RequestBody CreateItemTypeRequest req) {
        var type = itemTypeService.create(req.getParentTypeId(), req.getKey(),
                req.getName(), req.getDescription(), req.getValueSchema());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemMapper.toTypeResponse(type));
    }

    @Operation(summary = "Edit an item type")
    @PatchMapping("/item-types/{id}")
    public ResponseEntity<ItemTypeResponse> updateType(@PathVariable UUID id,
            @RequestBody UpdateItemTypeRequest req) {
        var type = itemTypeService.update(id, req.getName(), req.getDescription(), req.getValueSchema());
        return ResponseEntity.ok(ItemMapper.toTypeResponse(type));
    }

    @Operation(summary = "Toggle an item type", description = "Nothing gets deleted. Items of that "
            + "type stay put.")
    @PatchMapping("/item-types/{id}/active")
    public ResponseEntity<ItemTypeResponse> setTypeActive(@PathVariable UUID id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ItemMapper.toTypeResponse(itemTypeService.setActive(id, active)));
    }

    @Operation(summary = "Create an item")
    @PostMapping("/items")
    public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody CreateItemRequest req) {
        var item = itemService.create(req.getTypeId(), req.getName(), req.getDescription(),
                req.getIconUrl(), req.getValue(), req.getRarity(), req.isTradeable(), req.isVisible(),
                req.isStackable(), req.isWelcomeGrant(), req.isMissionPoolable(), req.isDownloadable(),
                req.isUniquePerUser(), req.isSerialized(), req.isActive(), req.getWorth(), req.getRequirement(),
                req.getUnlockLevel());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemMapper.toItemResponse(item));
    }

    @Operation(summary = "Edit an item")
    @PatchMapping("/items/{id}")
    public ResponseEntity<ItemResponse> updateItem(@PathVariable UUID id,
            @RequestBody UpdateItemRequest req) {
        var item = itemService.update(id, req.getName(), req.getDescription(), req.getIconUrl(),
                req.getValue(), req.getRarity(), req.getTradeable(), req.getVisible(),
                req.getStackable(), req.getWelcomeGrant(), req.getMissionPoolable(), req.getDownloadable(),
                req.getUniquePerUser(), req.getSerialized(), req.getWorth(), req.getRequirement(),
                req.getUnlockLevel());
        return ResponseEntity.ok(ItemMapper.toItemResponse(item));
    }

    @Operation(summary = "Retire or restore an item", description = "Players keep it. It drops out of the "
            + "catalogue and crate pools.")
    @PatchMapping("/items/{id}/active")
    public ResponseEntity<ItemResponse> setItemActive(@PathVariable UUID id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.setActive(id, active)));
    }

    @Operation(summary = "Give someone an item")
    @PostMapping("/items/award")
    public ResponseEntity<UserItemResponse> award(@Valid @RequestBody AwardItemRequest req,
            @AuthenticationPrincipal StaffUserDetails staff) {
        var link = itemService.awardManual(req.getUserId(), req.getItemId(),
                staff.getStaffUser(), req.getReason(), req.getModifierKeys(), req.getQuantity(),
                req.getUnusualEffectId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemMapper.toUserItemResponse(link, null));
    }

    @Operation(summary = "Set an item's cutoff",
            description = "After it, the item cannot be a campaign reward or go in a crate pool. Copies players hold "
                    + "are untouched. Leave the param off to clear it.")
    @PatchMapping("/items/{id}/obtainable-until")
    public ResponseEntity<ItemResponse> setObtainableUntil(@PathVariable UUID id,
            @RequestParam(required = false) Instant at) {
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.setObtainableUntil(id, at)));
    }

    @Operation(summary = "Deprecate an item")
    @PostMapping("/items/{id}/deprecate")
    public ResponseEntity<ItemResponse> deprecateItem(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.deprecate(id)));
    }

    @Operation(summary = "Set a modifier's drop chance and season")
    @PatchMapping("/item-modifiers/{id}")
    public ResponseEntity<ItemModifierResponse> updateModifier(@PathVariable UUID id,
            @RequestBody UpdateItemModifierRequest req) {
        var modifier = itemService.updateModifier(id, req.getGlobalDropChance(),
                req.getSeasonStart(), req.getSeasonEnd());
        return ResponseEntity.ok(ItemMapper.toModifierResponse(modifier));
    }

    @Operation(summary = "Take back an item award")
    @DeleteMapping("/items/awards/{linkId}")
    public ResponseEntity<Void> revokeAward(@PathVariable UUID linkId) {
        itemService.revokeAward(linkId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload an item image",
            description = "Also updates the rendered image for render items like badges.")
    @PostMapping(value = "/items/{id}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ItemResponse> uploadIcon(@PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        itemService.findByIdForStaff(id);
        String url = mediaProcessingService.storeImage(file, ITEM_ICON_SUBDIR, id.toString());
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.setUploadedImage(id, url)));
    }

    @Operation(summary = "Remove an item image")
    @DeleteMapping("/items/{id}/icon")
    public ResponseEntity<ItemResponse> deleteIcon(@PathVariable UUID id) {
        itemService.findByIdForStaff(id);
        mediaProcessingService.deleteIfExists(ITEM_ICON_SUBDIR, id.toString());
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.setIconUrl(id, null)));
    }
}
