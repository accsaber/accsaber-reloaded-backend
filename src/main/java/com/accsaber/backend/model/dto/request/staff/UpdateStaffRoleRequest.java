package com.accsaber.backend.model.dto.request.staff;

import com.accsaber.backend.model.entity.staff.StaffRole;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStaffRoleRequest {

    @NotNull
    private StaffRole role;
}
