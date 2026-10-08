package com.accsaber.backend.controller.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.milestone.UpsertLevelThresholdRequest;
import com.accsaber.backend.model.dto.response.milestone.LevelThresholdResponse;
import com.accsaber.backend.service.milestone.LevelService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/level-thresholds")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Milestones and Missions")
public class AdminLevelThresholdController {

    private final LevelService levelService;

    @Operation(summary = "All level thresholds")
    @GetMapping
    public ResponseEntity<List<LevelThresholdResponse>> list() {
        return ResponseEntity.ok(levelService.listThresholds());
    }

    @Operation(summary = "Get one level threshold")
    @GetMapping("/{level}")
    public ResponseEntity<LevelThresholdResponse> get(@PathVariable int level) {
        return ResponseEntity.ok(levelService.findThreshold(level));
    }

    @Operation(summary = "Set a level threshold")
    @PutMapping("/{level}")
    public ResponseEntity<LevelThresholdResponse> upsert(@PathVariable int level,
            @Valid @RequestBody UpsertLevelThresholdRequest request) {
        return ResponseEntity.ok(levelService.upsertThreshold(level, request));
    }

    @Operation(summary = "Delete a level threshold")
    @DeleteMapping("/{level}")
    public ResponseEntity<Void> delete(@PathVariable int level) {
        levelService.deleteThreshold(level);
        return ResponseEntity.noContent().build();
    }
}
