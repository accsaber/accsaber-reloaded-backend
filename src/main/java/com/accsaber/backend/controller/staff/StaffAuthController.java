package com.accsaber.backend.controller.staff;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.staff.LoginRequest;
import com.accsaber.backend.model.dto.request.staff.RefreshTokenRequest;
import com.accsaber.backend.model.dto.request.staff.StaffAccessRequest;
import com.accsaber.backend.model.dto.response.staff.AuthResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.security.StaffUserDetails;
import com.accsaber.backend.service.staff.StaffAuthService;
import com.accsaber.backend.service.staff.StaffUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/staff/auth")
@RequiredArgsConstructor
@Tag(name = "Staff Auth")
public class StaffAuthController {

    private final StaffAuthService staffAuthService;
    private final StaffUserService staffUserService;

    @Operation(summary = "Request staff access")
    @PostMapping("/request")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<Void> requestAccess(
            @Valid @RequestBody StaffAccessRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        staffUserService.requestAccess(request, principal.getUserId());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Log in as staff")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(staffAuthService.login(request));
    }

    @Operation(summary = "Refresh access token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(staffAuthService.refresh(request));
    }

    @Operation(summary = "Log out")
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal StaffUserDetails userDetails) {
        staffAuthService.logout(userDetails.getStaffUser().getId());
        return ResponseEntity.noContent().build();
    }
}
