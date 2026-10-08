package com.accsaber.backend.controller.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.item.UpsertCrateContentRequest;
import com.accsaber.backend.model.dto.request.item.UpsertCrateModifierRequest;
import com.accsaber.backend.model.dto.response.item.CrateContentResponse;
import com.accsaber.backend.model.dto.response.item.CrateModifierResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.accsaber.backend.model.dto.response.item.UnusualEffectResponse;
import com.accsaber.backend.model.entity.item.CrateContent;
import com.accsaber.backend.service.item.CrateService;
import com.accsaber.backend.service.item.ItemMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/crates")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Items and Crates")
public class AdminCrateController {

    private final CrateService crateService;

    @Operation(summary = "All crate items")
    @PreAuthorize("hasAnyRole('ADMIN', 'CREATIVE')")
    @GetMapping
    public ResponseEntity<List<ItemResponse>> listCrates() {
        return ResponseEntity.ok(crateService.listCrates().stream()
                .map(ItemMapper::toItemResponse)
                .toList());
    }

    @Operation(summary = "Crate reward pool with odds")
    @PreAuthorize("hasAnyRole('ADMIN', 'CREATIVE')")
    @GetMapping("/{crateItemId}/contents")
    public ResponseEntity<List<CrateContentResponse>> listContents(@PathVariable UUID crateItemId) {
        return ResponseEntity.ok(ItemMapper.toCrateContentResponses(crateService.listContents(crateItemId)));
    }

    @Operation(summary = "Add or update a crate reward")
    @PutMapping("/{crateItemId}/contents/{rewardItemId}")
    public ResponseEntity<CrateContentResponse> upsertContent(
            @PathVariable UUID crateItemId,
            @PathVariable UUID rewardItemId,
            @Valid @RequestBody UpsertCrateContentRequest req) {
        CrateContent saved = crateService.upsertContent(crateItemId, rewardItemId, req.getDropWeight());
        long total = crateService.listContents(crateItemId).stream().mapToLong(CrateContent::getDropWeight).sum();
        return ResponseEntity.ok(ItemMapper.toCrateContentResponse(saved, total));
    }

    @Operation(summary = "Remove a crate reward")
    @DeleteMapping("/{crateItemId}/contents/{rewardItemId}")
    public ResponseEntity<Void> removeContent(
            @PathVariable UUID crateItemId,
            @PathVariable UUID rewardItemId) {
        crateService.removeContent(crateItemId, rewardItemId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set a crate modifier chance")
    @PutMapping("/{crateItemId}/modifiers/{modifierId}")
    public ResponseEntity<CrateModifierResponse> upsertModifier(
            @PathVariable UUID crateItemId,
            @PathVariable UUID modifierId,
            @Valid @RequestBody UpsertCrateModifierRequest req) {
        return ResponseEntity.ok(ItemMapper.toCrateModifierResponse(
                crateService.upsertModifier(crateItemId, modifierId, req.getDropChance())));
    }

    @Operation(summary = "Detach a crate modifier")
    @DeleteMapping("/{crateItemId}/modifiers/{modifierId}")
    public ResponseEntity<Void> removeModifier(
            @PathVariable UUID crateItemId,
            @PathVariable UUID modifierId) {
        crateService.removeModifier(crateItemId, modifierId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add an unusual to a crate")
    @PutMapping("/{crateItemId}/unusual-effects/{effectId}")
    public ResponseEntity<UnusualEffectResponse> attachUnusualEffect(
            @PathVariable UUID crateItemId,
            @PathVariable UUID effectId) {
        return ResponseEntity.ok(ItemMapper.toUnusualEffectResponse(
                crateService.attachUnusualEffect(crateItemId, effectId)));
    }

    @Operation(summary = "Drop an unusual from a crate")
    @DeleteMapping("/{crateItemId}/unusual-effects/{effectId}")
    public ResponseEntity<Void> detachUnusualEffect(
            @PathVariable UUID crateItemId,
            @PathVariable UUID effectId) {
        crateService.detachUnusualEffect(crateItemId, effectId);
        return ResponseEntity.noContent().build();
    }
}
