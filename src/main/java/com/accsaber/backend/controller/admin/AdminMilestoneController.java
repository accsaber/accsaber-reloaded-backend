package com.accsaber.backend.controller.admin;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetRequest;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSchemaResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.service.milestone.MilestoneQueryBuilderService;
import com.accsaber.backend.service.milestone.MilestoneService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/milestones")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Milestones")
public class AdminMilestoneController {

    private final MilestoneService milestoneService;
    private final MilestoneQueryBuilderService queryBuilderService;

    @Operation(summary = "Get milestone query schema")
    @GetMapping("/schema")
    public ResponseEntity<MilestoneSchemaResponse> getSchema() {
        return ResponseEntity.ok(queryBuilderService.getSchema());
    }

    @Operation(summary = "Create a milestone set")
    @PostMapping("/sets")
    public ResponseEntity<MilestoneSetResponse> createSet(@Valid @RequestBody CreateMilestoneSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.createSet(request));
    }

    @Operation(summary = "Create a milestone")
    @PostMapping
    public ResponseEntity<MilestoneResponse> createMilestone(@Valid @RequestBody CreateMilestoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.createMilestone(request));
    }

    @Operation(summary = "Deactivate a milestone")
    @PatchMapping("/{id}")
    public ResponseEntity<Void> deactivateMilestone(@PathVariable UUID id) {
        milestoneService.deactivateMilestone(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Refresh milestone completion statistics")
    @PostMapping("/refresh-stats")
    public ResponseEntity<Void> refreshStats() {
        milestoneService.refreshCompletionStats();
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Backfill a milestone for all users")
    @PostMapping("/{id}/backfill")
    public ResponseEntity<Void> backfillMilestone(@PathVariable UUID id) {
        milestoneService.backfillMilestone(id);
        return ResponseEntity.accepted().build();
    }
}
