package com.accsaber.backend.model.dto.request.staff;

import com.accsaber.backend.model.entity.staff.StaffUserStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStaffStatusRequest {

    @NotNull
    private StaffUserStatus status;
}
