package com.accsaber.backend.model.dto.request.staff;

import com.accsaber.backend.model.entity.staff.StaffRole;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    private String identifier;

    @NotBlank
    private String password;

    private StaffRole role;
}
