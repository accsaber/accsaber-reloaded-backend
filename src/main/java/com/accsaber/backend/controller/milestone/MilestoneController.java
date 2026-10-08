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

import com.accsaber.backend.model.dto.response.milestone.MilestoneCompletionResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneHolderResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetGroupResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetLinkResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetResponse;
import com.accsaber.backend.model.dto.response.milestone.PrerequisiteLinkResponse;
import com.accsaber.backend.model.entity.milestone.LevelThreshold;
import com.accsaber.backend.service.infra.CategoryService;
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
    private final CategoryService categoryService;

    @Operation(summary = "List milestones", description = "The catalogue, not anyone's progress. Filter by set, "
            + "category UUID or code, or type: milestone or achievement.")
    @GetMapping("/milestones")
    public ResponseEntity<Page<MilestoneResponse>> listMilestones(
            @RequestParam(required = false) UUID setId,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity
                .ok(milestoneService.findAllActive(setId, categoryService.resolveId(categoryId), type, pageable));
    }

    @Operation(summary = "Milestone sets", description = "Pass userId to get that player's progress on each "
            + "set.")
    @GetMapping("/milestones/sets")
    public ResponseEntity<Page<MilestoneSetResponse>> listMilestoneSets(
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findAllSets(userId, pageable));
    }

    @Operation(summary = "Get one milestone")
    @GetMapping("/milestones/{id}")
    public ResponseEntity<MilestoneResponse> getMilestone(@PathVariable UUID id) {
        return ResponseEntity.ok(milestoneService.findById(id));
    }

    @Operation(summary = "Who has a milestone", description = "Most recent first.")
    @GetMapping("/milestones/{id}/holders")
    public ResponseEntity<Page<MilestoneHolderResponse>> getMilestoneHolders(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "completedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(milestoneService.findMilestoneHolders(id, pageable));
    }

    @Operation(summary = "Milestones in a set", description = "Flat list, no paging.")
    @GetMapping("/milestones/sets/{setId}/milestones")
    public ResponseEntity<List<MilestoneResponse>> getMilestonesBySet(@PathVariable UUID setId) {
        return ResponseEntity.ok(milestoneService.findBySet(setId));
    }

    @Operation(summary = "Set prerequisite links", description = "Which milestones unlock others. Use it "
            + "to draw the set as a tree.")
    @GetMapping("/milestones/sets/{setId}/prerequisites")
    public ResponseEntity<List<PrerequisiteLinkResponse>> getPrerequisiteLinksBySet(@PathVariable UUID setId) {
        return ResponseEntity.ok(milestoneService.findPrerequisiteLinksBySet(setId));
    }

    @Operation(summary = "List set groups")
    @GetMapping("/milestones/set-groups")
    public ResponseEntity<List<MilestoneSetGroupResponse>> listSetGroups() {
        return ResponseEntity.ok(milestoneService.findAllActiveGroups());
    }

    @Operation(summary = "Sets in a group", description = "In display order.")
    @GetMapping("/milestones/set-groups/{groupId}/links")
    public ResponseEntity<List<MilestoneSetLinkResponse>> getSetLinksByGroup(@PathVariable UUID groupId) {
        return ResponseEntity.ok(milestoneService.findSetLinksByGroup(groupId));
    }

    @Operation(summary = "Groups a set is in", description = "A set can be in more than one group.")
    @GetMapping("/milestones/sets/{setId}/groups")
    public ResponseEntity<List<MilestoneSetLinkResponse>> getSetLinksBySet(@PathVariable UUID setId) {
        return ResponseEntity.ok(milestoneService.findSetLinksBySet(setId));
    }

    @Operation(summary = "Milestone completion counts", description = "Pass userId to get that player's "
            + "state with each count. Sort defaults to tier.")
    @GetMapping("/milestones/completion-stats")
    public ResponseEntity<List<MilestoneCompletionResponse>> getCompletionStats(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "tier") String sort) {
        return ResponseEntity.ok(milestoneService.findAllCompletionStats(userId, sort));
    }

    @Operation(summary = "Level thresholds", description = "XP needed per level plus titles. Read them from "
            + "here, never hardcode them.")
    @GetMapping("/levels")
    public ResponseEntity<List<LevelThreshold>> listLevels() {
        return ResponseEntity.ok(levelService.getAllThresholds());
    }
}
