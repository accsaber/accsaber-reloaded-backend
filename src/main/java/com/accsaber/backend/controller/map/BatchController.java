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
import com.accsaber.backend.model.entity.map.BatchStatus;
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

    @Operation(summary = "List batches", description = "Paginated batch list, optionally filtered by status and/or batch name search")
    @GetMapping
    public ResponseEntity<Page<PublicBatchResponse>> listBatches(
            @RequestParam(required = false) BatchStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PublicBatchResponse> result = status != null
                ? batchService.findByStatusPublic(status, search, pageable)
                : batchService.findAllPublic(search, pageable);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get batch by ID", description = "Returns a batch with all its assigned map difficulties")
    @GetMapping("/{id}")
    public ResponseEntity<PublicBatchResponse> getBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(batchService.findByIdPublic(id));
    }
}
