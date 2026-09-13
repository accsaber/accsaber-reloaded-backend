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

    @Operation(summary = "List missions for any user",
            description = "Active missions by default, or the finished ones with completed=true. Pool narrows the active list "
                    + "and is ignored on the completed one.")
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

    @Operation(summary = "Open any community missions whose window is already running",
            description = "Community missions open on their own every hour, so this only exists to skip that wait after "
                    + "creating or editing a template. Safe to call repeatedly: it opens nothing that is already open, "
                    + "still closed, or past its completion cap. Returns how many it opened.")
    @PostMapping("/community/open")
    public ResponseEntity<Integer> openCommunityMissions() {
        return ResponseEntity.ok(sharedMissionService.openMissing());
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
