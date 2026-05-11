package com.accsaber.backend.controller.admin;

import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.item.AwardItemRequest;
import com.accsaber.backend.model.dto.request.item.CreateItemRequest;
import com.accsaber.backend.model.dto.request.item.CreateItemTypeRequest;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Items")
public class AdminItemController {

    private final ItemService itemService;
    private final ItemTypeService itemTypeService;

    @Operation(summary = "List item types (admin)")
    @GetMapping("/item-types")
    public ResponseEntity<List<ItemTypeResponse>> listTypes(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var types = includeInactive ? itemTypeService.findAll() : itemTypeService.findAllActive();
        return ResponseEntity.ok(types.stream().map(ItemMapper::toTypeResponse).toList());
    }

    @Operation(summary = "List items (admin)")
    @GetMapping("/items")
    public ResponseEntity<List<ItemResponse>> listItems(
            @RequestParam(required = false) UUID typeId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var items = typeId == null
                ? itemService.findAllForStaff(includeInactive)
                : itemService.findByTypeForStaff(typeId, includeInactive);
        return ResponseEntity.ok(items.stream().map(ItemMapper::toItemResponse).toList());
    }

    @Operation(summary = "Create an item type")
    @PostMapping("/item-types")
    public ResponseEntity<ItemTypeResponse> createType(@Valid @RequestBody CreateItemTypeRequest req) {
        var type = itemTypeService.create(req.getParentTypeId(), req.getKey(),
                req.getName(), req.getDescription(), req.getValueSchema());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemMapper.toTypeResponse(type));
    }

    @Operation(summary = "Update an item type")
    @PatchMapping("/item-types/{id}")
    public ResponseEntity<ItemTypeResponse> updateType(@PathVariable UUID id,
            @RequestBody UpdateItemTypeRequest req) {
        var type = itemTypeService.update(id, req.getName(), req.getDescription(), req.getValueSchema());
        return ResponseEntity.ok(ItemMapper.toTypeResponse(type));
    }

    @Operation(summary = "Deactivate an item type")
    @DeleteMapping("/item-types/{id}")
    public ResponseEntity<Void> deactivateType(@PathVariable UUID id) {
        itemTypeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivate a previously deactivated item type")
    @PostMapping("/item-types/{id}/reactivate")
    public ResponseEntity<ItemTypeResponse> reactivateType(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemMapper.toTypeResponse(itemTypeService.reactivate(id)));
    }

    @Operation(summary = "Create an item")
    @PostMapping("/items")
    public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody CreateItemRequest req) {
        var item = itemService.create(req.getTypeId(), req.getName(), req.getDescription(),
                req.getIconUrl(), req.getValue(), req.getRarity(), req.isTradeable(), req.isVisible(),
                req.isStackable(), req.isWelcomeGrant(), req.getWorth(), req.getRequirement(),
                req.getUnlockLevel());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemMapper.toItemResponse(item));
    }

    @Operation(summary = "Update an item")
    @PatchMapping("/items/{id}")
    public ResponseEntity<ItemResponse> updateItem(@PathVariable UUID id,
            @RequestBody UpdateItemRequest req) {
        var item = itemService.update(id, req.getName(), req.getDescription(), req.getIconUrl(),
                req.getValue(), req.getRarity(), req.getTradeable(), req.getVisible(),
                req.getStackable(), req.getWelcomeGrant(), req.getWorth(), req.getRequirement(),
                req.getUnlockLevel());
        return ResponseEntity.ok(ItemMapper.toItemResponse(item));
    }

    @Operation(summary = "Deactivate an item")
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deactivateItem(@PathVariable UUID id) {
        itemService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivate a previously deactivated item")
    @PostMapping("/items/{id}/reactivate")
    public ResponseEntity<ItemResponse> reactivateItem(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.reactivate(id)));
    }

    @Operation(summary = "Manually award an item to a user")
    @PostMapping("/items/award")
    public ResponseEntity<UserItemResponse> award(@Valid @RequestBody AwardItemRequest req,
            @AuthenticationPrincipal StaffUserDetails staff) {
        var link = itemService.awardManual(req.getUserId(), req.getItemId(),
                staff.getStaffUser(), req.getReason(), req.getModifierKeys(), req.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemMapper.toUserItemResponse(link));
    }

    @Operation(summary = "Mark an item as deprecated")
    @PostMapping("/items/{id}/deprecate")
    public ResponseEntity<ItemResponse> deprecateItem(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemMapper.toItemResponse(itemService.deprecate(id)));
    }

    @Operation(summary = "List all active item modifiers")
    @GetMapping("/item-modifiers")
    public ResponseEntity<List<ItemModifierResponse>> listModifiers() {
        return ResponseEntity.ok(itemService.findAllActiveModifiers().stream()
                .map(ItemMapper::toModifierResponse)
                .toList());
    }

    @Operation(summary = "Revoke a user's item award (hard delete)")
    @DeleteMapping("/items/awards/{linkId}")
    public ResponseEntity<Void> revokeAward(@PathVariable UUID linkId) {
        itemService.revokeAward(linkId);
        return ResponseEntity.noContent().build();
    }
}
