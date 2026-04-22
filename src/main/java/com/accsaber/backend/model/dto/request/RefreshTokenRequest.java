package com.accsaber.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = "refreshToken")
public class RefreshTokenRequest {

    @NotBlank
    private String refreshToken;
}
