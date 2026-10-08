package com.accsaber.backend.controller.ranking;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.map.BatchReweightRequest;
import com.accsaber.backend.model.dto.request.map.CreateBatchRequest;
import com.accsaber.backend.model.dto.request.map.UpdateBatchRequest;
import com.accsaber.backend.model.dto.request.map.UpdateBatchStatusRequest;
import com.accsaber.backend.model.dto.response.map.BatchResponse;
import com.accsaber.backend.model.dto.response.map.MapDifficultyResponse;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.map.BatchService;
import com.accsaber.backend.service.map.ReweightService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/ranking/batches")
@PreAuthorize("hasRole('RANKING_HEAD')")
@RequiredArgsConstructor
@Tag(name = "Ranking")
public class RankingBatchController {

    private final BatchService batchService;
    private final ReweightService reweightService;

    @Operation(summary = "List batches")
    @GetMapping
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<Page<BatchResponse>> listBatches(
            @RequestParam(required = false) BatchStatus status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        Page<BatchResponse> result = status != null
                ? batchService.findByStatus(status, search, pageable)
                : batchService.findAll(search, pageable);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get a batch")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<BatchResponse> getBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(batchService.findById(id));
    }

    @Operation(summary = "Create a batch", description = "Starts as a draft.")
    @PostMapping
    public ResponseEntity<BatchResponse> createBatch(
            @Valid @RequestBody CreateBatchRequest request,
            Authentication authentication) {
        BatchResponse response = batchService.create(request, StaffPrincipals.staffIdOf(authentication));
        return ResponseEntity.created(URI.create("/v1/batches/" + response.getId())).body(response);
    }

    @Operation(summary = "Edit a batch", description = "Name and description. Works in any status, even released.")
    @PatchMapping("/{id}")
    public ResponseEntity<BatchResponse> updateBatch(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBatchRequest request) {
        return ResponseEntity.ok(batchService.update(id, request));
    }

    @Operation(summary = "Set batch status", description = "draft or release_ready only. Use /release to publish.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<BatchResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBatchStatusRequest request) {
        return ResponseEntity.ok(batchService.updateStatus(id, request));
    }

    @Operation(summary = "Add a difficulty to a batch")
    @PostMapping("/{id}/difficulties/{difficultyId}")
    public ResponseEntity<BatchResponse> addDifficulty(
            @PathVariable UUID id,
            @PathVariable UUID difficultyId) {
        return ResponseEntity.ok(batchService.addDifficulty(id, difficultyId));
    }

    @Operation(summary = "Remove a difficulty from a batch")
    @DeleteMapping("/{id}/difficulties/{difficultyId}")
    public ResponseEntity<BatchResponse> removeDifficulty(
            @PathVariable UUID id,
            @PathVariable UUID difficultyId) {
        return ResponseEntity.ok(batchService.removeDifficulty(id, difficultyId));
    }

    @Operation(summary = "Release a batch", description = "Ranks every difficulty in it at once. Cannot be undone.")
    @PostMapping("/{id}/release")
    public ResponseEntity<BatchResponse> release(@PathVariable UUID id) {
        return ResponseEntity.ok(batchService.release(id));
    }

    @Operation(summary = "Reweight a batch", description = "Sets your complexities on its ranked maps, then adjusts "
            + "scores, stats and XP in the background. Items must be in the batch. Pinned maps get reweighted too.")
    @PostMapping("/{id}/reweight")
    public ResponseEntity<List<MapDifficultyResponse>> reweightBatch(
            @PathVariable UUID id,
            @Valid @RequestBody BatchReweightRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(reweightService.reweightBatch(id, request.getItems(),
                StaffPrincipals.linkedUserIdOf(authentication),
                StaffPrincipals.staffIdOf(authentication)));
    }

    @Operation(summary = "Recalculate a batch", description = "Recalculates scores in a released batch with current "
            + "complexities. Unchanged maps get skipped.")
    @PostMapping("/{id}/recalculate")
    public ResponseEntity<Void> recalculateBatch(@PathVariable UUID id) {
        reweightService.recalculateBatch(id);
        return ResponseEntity.accepted().build();
    }
}
