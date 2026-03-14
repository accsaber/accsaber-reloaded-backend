package com.accsaber.backend.model.dto.request.staff;

import lombok.Data;

@Data
public class UpdateStaffProfileRequest {

    private String username;

    private String email;
}
