package com.accsaber.backend.model.dto.request.discord;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LinkDiscordRequest {

    @NotBlank
    private String discordId;

    @NotBlank
    private String profileUrl;
}
