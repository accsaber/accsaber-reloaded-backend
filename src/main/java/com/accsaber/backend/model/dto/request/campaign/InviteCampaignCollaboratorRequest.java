package com.accsaber.backend.model.dto.request.campaign;

import jakarta.validation.constraints.NotNull;

public record InviteCampaignCollaboratorRequest(
        @NotNull Long userId) {
}
