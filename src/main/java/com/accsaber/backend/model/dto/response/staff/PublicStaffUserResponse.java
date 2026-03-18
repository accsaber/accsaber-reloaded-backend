package com.accsaber.backend.model.dto.response.staff;

import java.util.UUID;

import com.accsaber.backend.model.entity.staff.StaffRole;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublicStaffUserResponse {

    UUID id;
    String username;
    StaffRole role;
    String userId;
    String avatarUrl;
}
