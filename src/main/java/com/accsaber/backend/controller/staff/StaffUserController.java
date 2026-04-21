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
@Tag(name = "Staff Users")
@PreAuthorize("hasRole('ADMIN')")
public class StaffUserController {

    private final StaffUserService staffUserService;

    @Operation(summary = "List all staff users (active, inactive, any status)")
    @GetMapping
    public ResponseEntity<Page<StaffUserResponse>> getAll(
            @RequestParam(required = false) StaffUserStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(staffUserService.getAllUnfiltered(status, pageable));
    }

    @Operation(summary = "Update own username or email")
    @PatchMapping("/me")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<StaffUserResponse> updateProfile(
            @Valid @RequestBody UpdateStaffProfileRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(staffUserService.updateProfile(
                userDetails.getStaffUser().getId(), request));
    }

    @Operation(summary = "Create a staff user")
    @PostMapping
    public ResponseEntity<StaffUserResponse> create(@Valid @RequestBody CreateStaffUserRequest request) {
        StaffUserResponse response = staffUserService.create(request);
        return ResponseEntity.created(URI.create("/v1/staff/users/" + response.getId())).body(response);
    }

    @Operation(summary = "Update staff user role")
    @PatchMapping("/{id}/role")
    public ResponseEntity<StaffUserResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffRoleRequest request) {
        return ResponseEntity.ok(staffUserService.updateRole(id, request.getRole()));
    }

    @Operation(summary = "Update staff user status (requested/accepted/denied)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<StaffUserResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffStatusRequest request) {
        return ResponseEntity.ok(staffUserService.updateStatus(id, request.getStatus()));
    }

    @Operation(summary = "Link a player account to a staff user")
    @PatchMapping("/{id}/link-user")
    public ResponseEntity<StaffUserResponse> linkUser(
            @PathVariable UUID id,
            @Valid @RequestBody LinkUserRequest request) {
        return ResponseEntity.ok(staffUserService.linkUser(id, request.getUserId()));
    }

    @Operation(summary = "Force change a staff user's password")
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> forceChangePassword(
            @PathVariable UUID id,
            @Valid @RequestBody ForceChangePasswordRequest request) {
        staffUserService.forceChangePassword(id, request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Deactivate a staff user")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        staffUserService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

}
