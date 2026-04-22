package com.accsaber.backend.controller.map;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.map.PublicBatchResponse;
import com.accsaber.backend.service.map.BatchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/batches")
@RequiredArgsConstructor
@Tag(name = "Batches")
public class BatchController {

    private final BatchService batchService;

    @Operation(summary = "List released batches", description = "Paginated list of RELEASED batches, optionally filtered by batch name search. Draft and release-ready batches are not exposed here.")
    @GetMapping
    public ResponseEntity<Page<PublicBatchResponse>> listBatches(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(batchService.findAllPublic(search, pageable));
    }

    @Operation(summary = "Get released batch by ID", description = "Returns a RELEASED batch with all its assigned map difficulties. 404 for draft/release-ready batches.")
    @GetMapping("/{id}")
    public ResponseEntity<PublicBatchResponse> getBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(batchService.findByIdPublic(id));
    }
}
