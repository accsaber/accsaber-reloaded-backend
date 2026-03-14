package com.accsaber.backend.controller.milestone;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetRequest;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSchemaResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.dto.response.milestone.UserMilestoneProgressResponse;
import com.accsaber.backend.model.entity.milestone.LevelThreshold;
import com.accsaber.backend.service.milestone.LevelService;
import com.accsaber.backend.service.milestone.MilestoneQueryBuilderService;
import com.accsaber.backend.service.milestone.MilestoneService;
import com.accsaber.backend.service.player.UserService;
import com.accsaber.backend.service.score.XPReweightService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Milestones")
public class MilestoneController {

    private final MilestoneService milestoneService;
    private final LevelService levelService;
    private final UserService userService;
    private final XPReweightService xpReweightService;
    private final MilestoneQueryBuilderService queryBuilderService;

    @Operation(summary = "List active milestones with optional filters")
    @GetMapping("/milestones")
    public ResponseEntity<Page<MilestoneResponse>> getAllMilestones(
            @RequestParam(required = false) UUID setId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findAllActive(setId, categoryId, type, pageable));
    }

    @Operation(summary = "List active milestone sets")
    @GetMapping("/milestones/sets")
    public ResponseEntity<Page<MilestoneSetResponse>> getAllSets(
            @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findAllSets(pageable));
    }

    @Operation(summary = "Get a milestone by ID")
    @GetMapping("/milestones/{id}")
    public ResponseEntity<MilestoneResponse> getMilestone(@PathVariable UUID id) {
        return ResponseEntity.ok(milestoneService.findById(id));
    }

    @Operation(summary = "Get user milestone progress")
    @GetMapping("/users/{steamId}/milestones")
    public ResponseEntity<Page<UserMilestoneProgressResponse>> getUserMilestones(
            @PathVariable Long steamId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findUserProgress(steamId, pageable));
    }

    @Operation(summary = "Get user level and XP")
    @GetMapping("/users/{steamId}/level")
    public ResponseEntity<LevelResponse> getUserLevel(@PathVariable Long steamId) {
        var totalXp = userService.getTotalXp(steamId);
        return ResponseEntity.ok(levelService.calculateLevel(totalXp));
    }

    @Operation(summary = "List all level thresholds")
    @GetMapping("/levels")
    public ResponseEntity<List<LevelThreshold>> getAllLevels() {
        return ResponseEntity.ok(levelService.getAllThresholds());
    }

    @Operation(summary = "Get milestone query schema")
    @GetMapping("/admin/milestones/schema")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MilestoneSchemaResponse> getSchema() {
        return ResponseEntity.ok(queryBuilderService.getSchema());
    }

    @Operation(summary = "Create a milestone set")
    @PostMapping("/admin/milestones/sets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MilestoneSetResponse> createSet(@Valid @RequestBody CreateMilestoneSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.createSet(request));
    }

    @Operation(summary = "Create a milestone")
    @PostMapping("/admin/milestones")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MilestoneResponse> createMilestone(@Valid @RequestBody CreateMilestoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(milestoneService.createMilestone(request));
    }

    @Operation(summary = "Deactivate a milestone")
    @PatchMapping("/admin/milestones/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateMilestone(@PathVariable UUID id) {
        milestoneService.deactivateMilestone(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Refresh milestone completion statistics")
    @PostMapping("/admin/milestones/refresh-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> refreshStats() {
        milestoneService.refreshCompletionStats();
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Backfill a milestone for all users")
    @PostMapping("/admin/milestones/{id}/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> backfillMilestone(@PathVariable UUID id) {
        milestoneService.backfillMilestone(id);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Reweight XP for all scores")
    @PostMapping("/admin/xp/reweight")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reweightAllXp() {
        xpReweightService.reweightAllScores();
        return ResponseEntity.accepted().build();
    }
}
