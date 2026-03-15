package com.accsaber.backend.controller.milestone;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.entity.milestone.LevelThreshold;
import com.accsaber.backend.service.milestone.LevelService;
import com.accsaber.backend.service.milestone.MilestoneService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Milestones")
public class MilestoneController {

    private final MilestoneService milestoneService;
    private final LevelService levelService;

    @Operation(summary = "List active milestones with optional filters")
    @GetMapping("/milestones")
    public ResponseEntity<Page<MilestoneResponse>> listMilestones(
            @RequestParam(required = false) UUID setId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findAllActive(setId, categoryId, type, pageable));
    }

    @Operation(summary = "List active milestone sets")
    @GetMapping("/milestones/sets")
    public ResponseEntity<Page<MilestoneSetResponse>> listMilestoneSets(
            @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findAllSets(pageable));
    }

    @Operation(summary = "Get a milestone by ID")
    @GetMapping("/milestones/{id}")
    public ResponseEntity<MilestoneResponse> getMilestone(@PathVariable UUID id) {
        return ResponseEntity.ok(milestoneService.findById(id));
    }

    @Operation(summary = "List all level thresholds")
    @GetMapping("/levels")
    public ResponseEntity<List<LevelThreshold>> listLevels() {
        return ResponseEntity.ok(levelService.getAllThresholds());
    }
}
