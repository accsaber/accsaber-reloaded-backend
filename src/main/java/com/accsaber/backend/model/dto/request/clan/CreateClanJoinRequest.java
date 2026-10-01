package com.accsaber.backend.model.dto.request.clan;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateClanJoinRequest {

    @Pattern(regexp = "^\\d+$")
    private String userId;
}
