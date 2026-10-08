package com.accsaber.backend.controller.admin;

import java.util.List;
import java.util.Map;
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
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetGroupRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetRequest;
import com.accsaber.backend.model.dto.request.milestone.CreatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdateMilestoneRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdateMilestoneSetLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdateMilestoneSetRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSchemaResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetGroupResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetLinkResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.dto.response.milestone.PrerequisiteLinkResponse;
import com.accsaber.backend.model.entity.milestone.MilestoneStatus;
import com.accsaber.backend.service.milestone.MilestoneLayoutService;
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
@Tag(name = "Admin - Milestones and Missions")
public class AdminMilestoneController {

    private final MilestoneService milestoneService;
    private final MilestoneQueryBuilderService queryBuilderService;
    private final MilestoneLayoutService layoutService;

    @Operation(summary = "Milestone query schema")
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

    @Operation(summary = "List every milestone set")
    @GetMapping("/sets")
    public ResponseEntity<Page<MilestoneSetResponse>> listAllSets(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findAllSetsAdmin(pageable));
    }

    @Operation(summary = "New milestone set")
    @PostMapping("/sets")
    public ResponseEntity<MilestoneSetResponse> createSet(@Valid @RequestBody CreateMilestoneSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.createSet(request));
    }

    @Operation(summary = "Edit a milestone set")
    @PutMapping("/sets/{id}")
    public ResponseEntity<MilestoneSetResponse> updateSet(@PathVariable UUID id,
            @Valid @RequestBody UpdateMilestoneSetRequest request) {
        return ResponseEntity.ok(milestoneService.updateSet(id, request));
    }

    @Operation(summary = "New milestone")
    @PostMapping
    public ResponseEntity<MilestoneResponse> createMilestone(@Valid @RequestBody CreateMilestoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.createMilestone(request));
    }

    @Operation(summary = "Activate and backfill a milestone")
    @PostMapping("/activate/{id}")
    public ResponseEntity<MilestoneResponse> activateMilestone(@PathVariable UUID id) {
        MilestoneResponse response = milestoneService.activateMilestone(id);
        milestoneService.backfillMilestone(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Activate milestones in bulk")
    @PostMapping("/activate")
    public ResponseEntity<List<MilestoneResponse>> activateMilestones(
            @Valid @RequestBody ActivateMilestonesRequest request) {
        List<MilestoneResponse> responses = milestoneService.activateMilestones(request.getMilestoneIds());
        milestoneService.backfillAllMilestones();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Link maps to a milestone")
    @PostMapping("/{id}/map-links")
    public ResponseEntity<Void> addMapDifficultyLinks(@PathVariable UUID id,
            @Valid @RequestBody AddMapDifficultyLinksRequest request) {
        milestoneService.addMapDifficultyLinks(id, request.getMapDifficultyIds());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Unlink maps from a milestone")
    @DeleteMapping("/{id}/map-links")
    public ResponseEntity<Void> removeMapDifficultyLinks(@PathVariable UUID id,
            @Valid @RequestBody AddMapDifficultyLinksRequest request) {
        milestoneService.removeMapDifficultyLinks(id, request.getMapDifficultyIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Edit milestone name and text")
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

    @Operation(summary = "Remove a milestone and recalc XP")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeMilestone(@PathVariable UUID id) {
        milestoneService.removeMilestone(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Refresh milestone completion stats")
    @PostMapping("/refresh-stats")
    public ResponseEntity<Void> refreshStats() {
        milestoneService.refreshCompletionStats();
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Auto layout a milestone set", description = "Overwrites each "
            + "milestone's canvas position.")
    @PostMapping("/sets/{setId}/relayout")
    public ResponseEntity<Map<UUID, MilestoneLayoutService.Point>> relayoutSet(@PathVariable UUID setId) {
        return ResponseEntity.ok(layoutService.relayoutSet(setId));
    }

    @Operation(summary = "Add a prerequisite link")
    @PostMapping("/prerequisites")
    public ResponseEntity<PrerequisiteLinkResponse> createPrerequisiteLink(
            @Valid @RequestBody CreatePrerequisiteLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(milestoneService.createPrerequisiteLink(request));
    }

    @Operation(summary = "Edit a prerequisite link")
    @PutMapping("/prerequisites/{linkId}")
    public ResponseEntity<PrerequisiteLinkResponse> updatePrerequisiteLink(
            @PathVariable UUID linkId,
            @Valid @RequestBody UpdatePrerequisiteLinkRequest request) {
        return ResponseEntity.ok(milestoneService.updatePrerequisiteLink(linkId, request));
    }

    @Operation(summary = "Drop a prerequisite link")
    @DeleteMapping("/prerequisites/{linkId}")
    public ResponseEntity<Void> deactivatePrerequisiteLink(@PathVariable UUID linkId) {
        milestoneService.deactivatePrerequisiteLink(linkId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "A milestone's prerequisites")
    @GetMapping("/{id}/prerequisites")
    public ResponseEntity<List<PrerequisiteLinkResponse>> getPrerequisites(@PathVariable UUID id) {
        return ResponseEntity.ok(milestoneService.findPrerequisitesByMilestone(id));
    }

    @Operation(summary = "New set group")
    @PostMapping("/set-groups")
    public ResponseEntity<MilestoneSetGroupResponse> createSetGroup(
            @Valid @RequestBody CreateMilestoneSetGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.createSetGroup(request));
    }

    @Operation(summary = "Edit a set group")
    @PutMapping("/set-groups/{groupId}")
    public ResponseEntity<MilestoneSetGroupResponse> updateSetGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateMilestoneSetGroupRequest request) {
        return ResponseEntity.ok(milestoneService.updateSetGroup(groupId, request));
    }

    @Operation(summary = "Deactivate a set group")
    @DeleteMapping("/set-groups/{groupId}")
    public ResponseEntity<Void> deactivateSetGroup(@PathVariable UUID groupId) {
        milestoneService.deactivateSetGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add a set to a group")
    @PostMapping("/set-links")
    public ResponseEntity<MilestoneSetLinkResponse> createSetLink(
            @Valid @RequestBody CreateMilestoneSetLinkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.createSetLink(request));
    }

    @Operation(summary = "Reorder a set link")
    @PutMapping("/set-links/{linkId}")
    public ResponseEntity<MilestoneSetLinkResponse> updateSetLink(
            @PathVariable UUID linkId,
            @Valid @RequestBody UpdateMilestoneSetLinkRequest request) {
        return ResponseEntity.ok(milestoneService.updateSetLink(linkId, request));
    }

    @Operation(summary = "Drop a set link")
    @DeleteMapping("/set-links/{linkId}")
    public ResponseEntity<Void> deactivateSetLink(@PathVariable UUID linkId) {
        milestoneService.deactivateSetLink(linkId);
        return ResponseEntity.noContent().build();
    }
}
