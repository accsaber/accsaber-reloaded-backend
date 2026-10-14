package com.accsaber.backend.model.dto.request.staff;

import com.accsaber.backend.model.entity.staff.StaffUserStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateStaffStatusRequest(
        @NotNull StaffUserStatus status) {
}
