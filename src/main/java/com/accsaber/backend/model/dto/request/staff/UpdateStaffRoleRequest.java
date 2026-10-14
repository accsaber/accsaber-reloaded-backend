package com.accsaber.backend.model.dto.request.staff;

import com.accsaber.backend.model.entity.staff.StaffRole;

import jakarta.validation.constraints.NotNull;

public record UpdateStaffRoleRequest(
        @NotNull StaffRole role) {
}
