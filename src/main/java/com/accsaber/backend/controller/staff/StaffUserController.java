package com.accsaber.backend.controller.staff;

import com.accsaber.backend.model.dto.request.staff.CreateStaffUserRequest;
import com.accsaber.backend.model.dto.request.staff.OAuthLinkRequest;
import com.accsaber.backend.model.dto.request.staff.UpdateStaffProfileRequest;
import com.accsaber.backend.model.dto.request.staff.UpdateStaffRoleRequest;
import com.accsaber.backend.model.dto.request.staff.UpdateStaffStatusRequest;
import com.accsaber.backend.model.dto.response.staff.StaffOAuthLinkResponse;
import com.accsaber.backend.model.dto.response.staff.StaffUserResponse;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.staff.StaffUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/staff/users")
@RequiredArgsConstructor
@Tag(name = "Staff Users")
@PreAuthorize("hasRole('ADMIN')")
public class StaffUserController {

    private final StaffUserService staffUserService;

    @Operation(summary = "Update own username or email")
    @PatchMapping("/me")
    @PreAuthorize("hasRole('RANKING')")
    public ResponseEntity<StaffUserResponse> updateProfile(
            @Valid @RequestBody UpdateStaffProfileRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(staffUserService.updateProfile(
                userDetails.getStaffUser().getId(), request));
    }

    @Operation(summary = "List all active staff users")
    @GetMapping
    public ResponseEntity<Page<StaffUserResponse>> listStaffUsers(
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return ResponseEntity.ok(staffUserService.getAll(pageable));
    }

    @Operation(summary = "Get staff user by ID")
    @GetMapping("/{id}")
    public ResponseEntity<StaffUserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(staffUserService.getById(id));
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

    @Operation(summary = "Deactivate a staff user")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        staffUserService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Link an OAuth provider to a staff user")
    @PostMapping("/{id}/oauth")
    public ResponseEntity<StaffOAuthLinkResponse> linkOAuth(
            @PathVariable UUID id,
            @Valid @RequestBody OAuthLinkRequest request,
            @AuthenticationPrincipal StaffUserDetails userDetails) {
        StaffOAuthLinkResponse response = staffUserService.linkOAuth(
                id, request, userDetails.getStaffUser().getId());
        return ResponseEntity.created(URI.create("/v1/staff/users/" + id + "/oauth/" + response.getId()))
                .body(response);
    }

    @Operation(summary = "Unlink an OAuth provider from a staff user")
    @DeleteMapping("/{id}/oauth/{linkId}")
    public ResponseEntity<Void> unlinkOAuth(@PathVariable UUID id, @PathVariable UUID linkId) {
        staffUserService.unlinkOAuth(linkId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get OAuth links for a staff user")
    @GetMapping("/{id}/oauth")
    public ResponseEntity<List<StaffOAuthLinkResponse>> getOAuthLinks(@PathVariable UUID id) {
        return ResponseEntity.ok(staffUserService.getOAuthLinks(id));
    }
}
