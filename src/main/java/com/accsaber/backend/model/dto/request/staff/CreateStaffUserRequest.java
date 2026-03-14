package com.accsaber.backend.model.dto.request.staff;

import com.accsaber.backend.model.entity.staff.StaffRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateStaffUserRequest {

    private String username;

    private String email;

    @NotBlank
    private String password;

    @NotNull
    private StaffRole role;

    private Long userId;
}
