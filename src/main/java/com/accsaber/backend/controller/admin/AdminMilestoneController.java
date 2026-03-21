package com.accsaber.backend.controller.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.milestone.ActivateMilestonesRequest;
import com.accsaber.backend.model.dto.request.milestone.AddMapDifficultyLinksRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetRequest;
import com.accsaber.backend.model.dto.request.milestone.CreatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdateMilestoneRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSchemaResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.dto.response.milestone.PrerequisiteLinkResponse;
import com.accsaber.backend.model.entity.milestone.MilestoneStatus;
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

    @Operation(summary = "List milestones by status")
    @GetMapping
    public ResponseEntity<Page<MilestoneResponse>> listMilestones(
            @RequestParam(required = false) UUID setId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "DRAFT") MilestoneStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findAllByStatus(setId, categoryId, type, status, pageable));
    }

    @Operation(summary = "List all milestone sets (active and inactive)")
    @GetMapping("/sets")
    public ResponseEntity<Page<MilestoneSetResponse>> listAllSets(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findAllSetsAdmin(pageable));
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

    @Operation(summary = "Activate a milestone and backfill all users")
    @PostMapping("/activate/{id}")
    public ResponseEntity<MilestoneResponse> activateMilestone(@PathVariable UUID id) {
        MilestoneResponse response = milestoneService.activateMilestone(id);
        milestoneService.backfillMilestone(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Activate multiple milestones and backfill all users")
    @PostMapping("/activate")
    public ResponseEntity<List<MilestoneResponse>> activateMilestones(
            @Valid @RequestBody ActivateMilestonesRequest request) {
        List<MilestoneResponse> responses = milestoneService.activateMilestones(request.getMilestoneIds());
        for (MilestoneResponse milestone : responses) {
            milestoneService.backfillMilestone(milestone.getId());
        }
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Add map difficulty links to a milestone")
    @PostMapping("/{id}/map-links")
    public ResponseEntity<Void> addMapDifficultyLinks(@PathVariable UUID id,
            @Valid @RequestBody AddMapDifficultyLinksRequest request) {
        milestoneService.addMapDifficultyLinks(id, request.getMapDifficultyIds());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Remove map difficulty links from a milestone")
    @DeleteMapping("/{id}/map-links")
    public ResponseEntity<Void> removeMapDifficultyLinks(@PathVariable UUID id,
            @Valid @RequestBody AddMapDifficultyLinksRequest request) {
        milestoneService.removeMapDifficultyLinks(id, request.getMapDifficultyIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update milestone name/description")
    @PutMapping("/{id}")
    public ResponseEntity<MilestoneResponse> updateMilestone(@PathVariable UUID id,
            @Valid @RequestBody UpdateMilestoneRequest request) {
        return ResponseEntity.ok(milestoneService.updateMilestone(id, request));
    }

    @Operation(summary = "Deactivate a milestone")
    @PatchMapping("/{id}/deactivate")
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

    @Operation(summary = "Create a prerequisite link between milestones")
    @PostMapping("/prerequisites")
    public ResponseEntity<PrerequisiteLinkResponse> createPrerequisiteLink(
            @Valid @RequestBody CreatePrerequisiteLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(milestoneService.createPrerequisiteLink(request));
    }

    @Operation(summary = "Update a prerequisite link")
    @PutMapping("/prerequisites/{linkId}")
    public ResponseEntity<PrerequisiteLinkResponse> updatePrerequisiteLink(
            @PathVariable UUID linkId,
            @Valid @RequestBody UpdatePrerequisiteLinkRequest request) {
        return ResponseEntity.ok(milestoneService.updatePrerequisiteLink(linkId, request));
    }

    @Operation(summary = "Deactivate a prerequisite link")
    @DeleteMapping("/prerequisites/{linkId}")
    public ResponseEntity<Void> deactivatePrerequisiteLink(@PathVariable UUID linkId) {
        milestoneService.deactivatePrerequisiteLink(linkId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get prerequisite links for a milestone")
    @GetMapping("/{id}/prerequisites")
    public ResponseEntity<List<PrerequisiteLinkResponse>> getPrerequisites(@PathVariable UUID id) {
        return ResponseEntity.ok(milestoneService.findPrerequisitesByMilestone(id));
    }
}
