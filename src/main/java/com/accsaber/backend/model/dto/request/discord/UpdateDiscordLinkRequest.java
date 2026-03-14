package com.accsaber.backend.model.dto.request.discord;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDiscordLinkRequest {

    @NotBlank
    private String profileUrl;
}
