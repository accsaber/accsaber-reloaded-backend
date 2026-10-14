package com.accsaber.backend.model.dto.request.discord;

import jakarta.validation.constraints.NotBlank;

public record UpdateDiscordLinkRequest(
        @NotBlank String profileUrl) {
}
