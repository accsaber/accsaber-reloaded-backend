package com.accsaber.backend.controller.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.mission.MissionTemplateRequest;
import com.accsaber.backend.model.dto.response.mission.MissionTemplateResponse;
import com.accsaber.backend.model.dto.response.mission.MissionResponse;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.service.mission.SharedMissionService;
import com.accsaber.backend.service.mission.MissionAssignmentService;
import com.accsaber.backend.service.mission.MissionQueryService;
import com.accsaber.backend.service.mission.MissionTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/missions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Milestones and Missions")
public class AdminMissionController {

    private final MissionTemplateService templateService;
    private final MissionAssignmentService assignmentService;
    private final MissionQueryService queryService;
    private final SharedMissionService sharedMissionService;

    @Operation(summary = "All mission templates")
    @GetMapping("/templates")
    public ResponseEntity<List<MissionTemplateResponse>> listTemplates() {
        return ResponseEntity.ok(templateService.listAll().stream()
                .map(MissionTemplateResponse::from).toList());
    }

    @Operation(summary = "New mission template")
    @PostMapping("/templates")
    public ResponseEntity<MissionTemplateResponse> createTemplate(@Valid @RequestBody MissionTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MissionTemplateResponse.from(templateService.create(req)));
    }

    @Operation(summary = "Edit a mission template")
    @PatchMapping("/templates/{id}")
    public ResponseEntity<MissionTemplateResponse> updateTemplate(@PathVariable UUID id,
            @Valid @RequestBody MissionTemplateRequest req) {
        return ResponseEntity.ok(MissionTemplateResponse.from(templateService.update(id, req)));
    }

    @Operation(summary = "Deactivate a mission template")
    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reroll a user's missions",
            description = "Leave pool empty for daily and weekly. Set it to refresh only that pool.")
    @PostMapping("/users/{userId}/regenerate")
    public ResponseEntity<List<MissionResponse>> regenerate(@PathVariable Long userId,
            @RequestParam(required = false) MissionPool pool) {
        return ResponseEntity.ok(
                assignmentService.regenerateForUser(userId, pool).stream()
                        .map(MissionResponse::from).toList());
    }

    @Operation(summary = "Any user's missions",
            description = "Active by default. completed=true gives finished ones. Pool only filters the active list.")
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<MissionResponse>> listForUser(@PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean completed,
            @RequestParam(required = false) MissionPool pool) {
        return ResponseEntity.ok(resolveMissions(userId, completed, pool).stream()
                .map(MissionResponse::from).toList());
    }

    private List<UserMission> resolveMissions(Long userId, boolean completed, MissionPool pool) {
        if (completed) {
            return queryService.listCompleted(userId);
        }
        return pool == null
                ? queryService.listActive(userId)
                : queryService.listActiveByPool(userId, pool);
    }

    @Operation(summary = "Open due community missions",
            description = "They open on their own every hour. This skips the wait. Safe to repeat, returns how many "
                    + "opened.")
    @PostMapping("/community/open")
    public ResponseEntity<Integer> openCommunityMissions() {
        return ResponseEntity.ok(sharedMissionService.openMissing());
    }

    @Operation(summary = "Reroll missions for everyone",
            description = "Async. Leave pool empty for daily and weekly. Wipes active and expired missions in that "
                    + "pool, then rerolls everyone.")
    @PostMapping("/rollout")
    public ResponseEntity<Void> rolloutAll(@RequestParam(required = false) MissionPool pool) {
        if (pool == null) {
            assignmentService.rolloutAllUsers(true);
        } else {
            assignmentService.rolloutPool(pool, true);
        }
        return ResponseEntity.accepted().build();
    }
}
