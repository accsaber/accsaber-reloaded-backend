package com.accsaber.backend.model.dto.request;

import com.accsaber.backend.model.entity.user.UserRelationType;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRelationRequest {

    @NotNull
    private Long targetUserId;

    @NotNull
    private UserRelationType type;
}
