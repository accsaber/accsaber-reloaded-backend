package com.accsaber.backend.controller.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.item.CrateContentResponse;
import com.accsaber.backend.model.dto.response.item.CrateModifierResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.item.UnusualEffectResponse;
import com.accsaber.backend.service.item.CrateService;
import com.accsaber.backend.service.item.ItemMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/staff/crates")
@PreAuthorize("hasAnyRole('ADMIN', 'CREATIVE')")
@RequiredArgsConstructor
@Tag(name = "Staff Items")
public class StaffCrateController {

    private final CrateService crateService;

    @Operation(summary = "List all crate items, for staff browsing and previewing")
    @GetMapping
    public ResponseEntity<List<ItemResponse>> listCrates() {
        return ResponseEntity.ok(crateService.listCrates().stream()
                .map(ItemMapper::toItemResponse)
                .toList());
    }

    @Operation(summary = "List a crate's full reward pool including unreleased items, for staff previewing")
    @GetMapping("/{crateItemId}/contents")
    public ResponseEntity<List<CrateContentResponse>> listContents(@PathVariable UUID crateItemId) {
        return ResponseEntity.ok(ItemMapper.toCrateContentResponses(crateService.listContents(crateItemId)));
    }

    @Operation(summary = "List the modifiers a crate can roll onto its reward, with drop chances")
    @GetMapping("/{crateItemId}/modifiers")
    public ResponseEntity<List<CrateModifierResponse>> listModifiers(@PathVariable UUID crateItemId) {
        return ResponseEntity.ok(ItemMapper.toCrateModifierResponses(crateService.listModifiers(crateItemId)));
    }

    @Operation(summary = "List the unusual effects a crate can roll (equal chance among them)")
    @GetMapping("/{crateItemId}/unusual-effects")
    public ResponseEntity<List<UnusualEffectResponse>> listUnusualEffects(@PathVariable UUID crateItemId) {
        return ResponseEntity.ok(crateService.listUnusualEffects(crateItemId).stream()
                .map(ItemMapper::toUnusualEffectResponse)
                .toList());
    }
}
