package com.accsaber.backend.controller.map;

import java.net.URI;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.map.CreateBatchRequest;
import com.accsaber.backend.model.dto.request.map.UpdateBatchStatusRequest;
import com.accsaber.backend.model.dto.response.map.BatchResponse;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.map.BatchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/batches")
@RequiredArgsConstructor
@Tag(name = "Batches")
public class BatchController {

    private final BatchService batchService;

    @Operation(summary = "List batches", description = "Paginated batch list, optionally filtered by status")
    @GetMapping
    public ResponseEntity<Page<BatchResponse>> listBatches(
            @RequestParam(required = false) BatchStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<BatchResponse> result = status != null
                ? batchService.findByStatus(status, pageable)
                : batchService.findAll(pageable);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get batch by ID", description = "Returns a batch with all its assigned map difficulties")
    @GetMapping("/{id}")
    public ResponseEntity<BatchResponse> getBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(batchService.findById(id));
    }

    @Operation(summary = "Create a batch", description = "Creates a new batch in draft status")
    @PostMapping
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<BatchResponse> createBatch(
            @Valid @RequestBody CreateBatchRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        BatchResponse response = batchService.create(request, userDetails.getStaffUser().getId());
        return ResponseEntity.created(URI.create("/v1/batches/" + response.getId())).body(response);
    }

    @Operation(summary = "Update batch status", description = "Transitions a batch between draft and release_ready. Use /release to publish.")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<BatchResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBatchStatusRequest request) {
        return ResponseEntity.ok(batchService.updateStatus(id, request));
    }

    @Operation(summary = "Add a map difficulty to a batch")
    @PostMapping("/{id}/difficulties/{difficultyId}")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<BatchResponse> addDifficulty(
            @PathVariable UUID id,
            @PathVariable UUID difficultyId) {
        return ResponseEntity.ok(batchService.addDifficulty(id, difficultyId));
    }

    @Operation(summary = "Remove a map difficulty from a batch")
    @DeleteMapping("/{id}/difficulties/{difficultyId}")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<BatchResponse> removeDifficulty(
            @PathVariable UUID id,
            @PathVariable UUID difficultyId) {
        return ResponseEntity.ok(batchService.removeDifficulty(id, difficultyId));
    }

    @Operation(summary = "Release a batch", description = "Atomically transitions all member difficulties to ranked and stamps ranked_at. Irreversible.")
    @PostMapping("/{id}/release")
    @PreAuthorize("hasRole('RANKING_HEAD')")
    public ResponseEntity<BatchResponse> release(@PathVariable UUID id) {
        return ResponseEntity.ok(batchService.release(id));
    }
}
