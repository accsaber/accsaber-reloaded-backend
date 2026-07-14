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
@Tag(name = "Admin Missions")
public class AdminMissionController {

    private final MissionTemplateService templateService;
    private final MissionAssignmentService assignmentService;
    private final MissionQueryService queryService;

    @Operation(summary = "List all mission templates")
    @GetMapping("/templates")
    public ResponseEntity<List<MissionTemplateResponse>> listTemplates() {
        return ResponseEntity.ok(templateService.listAll().stream()
                .map(MissionTemplateResponse::from).toList());
    }

    @Operation(summary = "Create a mission template")
    @PostMapping("/templates")
    public ResponseEntity<MissionTemplateResponse> createTemplate(@Valid @RequestBody MissionTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MissionTemplateResponse.from(templateService.create(req)));
    }

    @Operation(summary = "Update a mission template")
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

    @Operation(summary = "Regenerate missions for a user",
            description = "Pool optional. Omit to regenerate both daily and weekly; specify to refresh only that pool.")
    @PostMapping("/users/{userId}/regenerate")
    public ResponseEntity<List<MissionResponse>> regenerate(@PathVariable Long userId,
            @RequestParam(required = false) MissionPool pool) {
        return ResponseEntity.ok(
                assignmentService.regenerateForUser(userId, pool).stream()
                        .map(MissionResponse::from).toList());
    }

    @Operation(summary = "List active missions for any user")
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<MissionResponse>> listForUser(@PathVariable Long userId,
            @RequestParam(required = false) MissionPool pool) {
        var missions = pool == null
                ? queryService.listActive(userId)
                : queryService.listActiveByPool(userId, pool);
        return ResponseEntity.ok(missions.stream().map(MissionResponse::from).toList());
    }

    @Operation(summary = "List completed missions for any user")
    @GetMapping("/users/{userId}/completed")
    public ResponseEntity<List<MissionResponse>> listCompletedForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(
                queryService.listCompleted(userId).stream()
                        .map(MissionResponse::from).toList());
    }

    @Operation(summary = "Force a fresh mission rollout for ALL eligible users",
            description = "Async. Pool optional: omit to roll both daily and weekly, or specify to roll only that pool. Purges active+expired for the targeted pool(s), then re-rolls per user with fresh random seeds.")
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
