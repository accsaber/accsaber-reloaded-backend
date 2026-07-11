package com.accsaber.backend.controller.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.service.item.ItemMapper;
import com.accsaber.backend.service.item.ItemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/staff/items")
@PreAuthorize("hasAnyRole('ADMIN', 'CREATIVE')")
@RequiredArgsConstructor
@Tag(name = "Staff Items")
public class StaffItemController {

    private final ItemService itemService;

    @Operation(summary = "List items including unreleased drafts and deactivated items, for staff browsing and previewing")
    @GetMapping
    public ResponseEntity<List<ItemResponse>> listItems(
            @RequestParam(required = false) UUID typeId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        var items = typeId == null
                ? itemService.findAllForStaff(includeInactive)
                : itemService.findByTypeForStaff(typeId, includeInactive);
        return ResponseEntity.ok(items.stream().map(ItemMapper::toItemResponse).toList());
    }
}
