package com.accsaber.backend.model.dto.request.user;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MergeUsersRequest {

    @NotNull
    private Long primaryUserId;

    @NotNull
    private Long secondaryUserId;

    private String reason;
}
