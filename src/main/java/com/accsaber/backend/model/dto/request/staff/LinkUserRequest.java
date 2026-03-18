package com.accsaber.backend.model.dto.request.staff;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LinkUserRequest {

    @NotNull
    private Long userId;
}
