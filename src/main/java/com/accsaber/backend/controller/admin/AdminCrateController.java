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
import com.accsaber.backend.model.dto.response.item.CrateContentResponse;
import com.accsaber.backend.model.dto.response.item.ItemResponse;
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
@Tag(name = "Admin Crates")
public class AdminCrateController {

    private final CrateService crateService;

    @Operation(summary = "List all crate items")
    @GetMapping
    public ResponseEntity<List<ItemResponse>> listCrates() {
        return ResponseEntity.ok(crateService.listCrates().stream()
                .map(ItemMapper::toItemResponse)
                .toList());
    }

    @Operation(summary = "List the reward pool of a crate with drop weights and normalized drop chances")
    @GetMapping("/{crateItemId}/contents")
    public ResponseEntity<List<CrateContentResponse>> listContents(@PathVariable UUID crateItemId) {
        List<CrateContent> contents = crateService.listContents(crateItemId);
        long total = contents.stream().mapToLong(CrateContent::getDropWeight).sum();
        return ResponseEntity.ok(contents.stream()
                .map(c -> ItemMapper.toCrateContentResponse(c, total))
                .toList());
    }

    @Operation(summary = "Add a reward to a crate, or update its drop weight")
    @PutMapping("/{crateItemId}/contents/{rewardItemId}")
    public ResponseEntity<CrateContentResponse> upsertContent(
            @PathVariable UUID crateItemId,
            @PathVariable UUID rewardItemId,
            @Valid @RequestBody UpsertCrateContentRequest req) {
        CrateContent saved = crateService.upsertContent(crateItemId, rewardItemId, req.getDropWeight());
        long total = crateService.listContents(crateItemId).stream().mapToLong(CrateContent::getDropWeight).sum();
        return ResponseEntity.ok(ItemMapper.toCrateContentResponse(saved, total));
    }

    @Operation(summary = "Remove a reward from a crate's pool")
    @DeleteMapping("/{crateItemId}/contents/{rewardItemId}")
    public ResponseEntity<Void> removeContent(
            @PathVariable UUID crateItemId,
            @PathVariable UUID rewardItemId) {
        crateService.removeContent(crateItemId, rewardItemId);
        return ResponseEntity.noContent().build();
    }
}
