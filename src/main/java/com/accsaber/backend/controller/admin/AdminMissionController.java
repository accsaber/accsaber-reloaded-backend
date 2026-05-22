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
import com.accsaber.backend.model.dto.response.mission.UserMissionResponse;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.service.mission.MissionAssignmentService;
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

    @Operation(summary = "Regenerate today's missions for a user")
    @PostMapping("/users/{userId}/regenerate")
    public ResponseEntity<List<UserMissionResponse>> regenerate(@PathVariable Long userId,
            @RequestParam(defaultValue = "daily") MissionPool pool) {
        return ResponseEntity.ok(
                assignmentService.regenerateForUser(userId, pool).stream()
                        .map(UserMissionResponse::from).toList());
    }
}
