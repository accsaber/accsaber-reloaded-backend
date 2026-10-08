package com.accsaber.backend.controller.staff;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.staff.CreateStaffUserRequest;
import com.accsaber.backend.model.dto.request.staff.ForceChangePasswordRequest;
import com.accsaber.backend.model.dto.request.staff.LinkUserRequest;
import com.accsaber.backend.model.dto.request.staff.UpdateStaffProfileRequest;
import com.accsaber.backend.model.dto.request.staff.UpdateStaffRoleRequest;
import com.accsaber.backend.model.dto.request.staff.UpdateStaffStatusRequest;
import com.accsaber.backend.model.dto.response.staff.StaffUserResponse;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.staff.StaffUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/staff/users")
@RequiredArgsConstructor
@Tag(name = "Staff Accounts")
@PreAuthorize("hasRole('ADMIN')")
public class StaffUserController {

    private final StaffUserService staffUserService;

    @Operation(summary = "List all staff users")
    @GetMapping
    public ResponseEntity<Page<StaffUserResponse>> getAll(
            @RequestParam(required = false) StaffUserStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(staffUserService.getAllUnfiltered(status, pageable));
    }

    @Operation(summary = "Change your username or email")
    @PatchMapping("/me")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<StaffUserResponse> updateProfile(
            @Valid @RequestBody UpdateStaffProfileRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(staffUserService.updateProfile(
                userDetails.getStaffUser().getId(), request));
    }

    @Operation(summary = "New staff user")
    @PostMapping
    public ResponseEntity<StaffUserResponse> create(@Valid @RequestBody CreateStaffUserRequest request) {
        StaffUserResponse response = staffUserService.create(request);
        return ResponseEntity.created(URI.create("/v1/staff/users/" + response.getId())).body(response);
    }

    @Operation(summary = "Set a staff role")
    @PatchMapping("/{id}/role")
    public ResponseEntity<StaffUserResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffRoleRequest request) {
        return ResponseEntity.ok(staffUserService.updateRole(id, request.getRole()));
    }

    @Operation(summary = "Set staff status")
    @PatchMapping("/{id}/status")
    public ResponseEntity<StaffUserResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffStatusRequest request) {
        return ResponseEntity.ok(staffUserService.updateStatus(id, request.getStatus()));
    }

    @Operation(summary = "Link a player to staff")
    @PatchMapping("/{id}/link-user")
    public ResponseEntity<StaffUserResponse> linkUser(
            @PathVariable UUID id,
            @Valid @RequestBody LinkUserRequest request) {
        return ResponseEntity.ok(staffUserService.linkUser(id, request.getUserId()));
    }

    @Operation(summary = "Reset a staff password")
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> forceChangePassword(
            @PathVariable UUID id,
            @Valid @RequestBody ForceChangePasswordRequest request) {
        staffUserService.forceChangePassword(id, request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lock or unlock staff", description = "active=false locks them out and kills "
            + "their sessions. active=true brings them back. 409 if another active account has the same email or "
            + "the same username for that role.")
    @PatchMapping("/{id}/active")
    public ResponseEntity<StaffUserResponse> setActive(@PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(staffUserService.setActive(id, active));
    }

    @Operation(summary = "Delete a staff user", description = "Removes the account and its votes and "
            + "clears its credits everywhere. 409 if it wrote news posts or admin actions.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        staffUserService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
